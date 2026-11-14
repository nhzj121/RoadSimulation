package org.example.roadsimulation.sandbox.optimization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.sandbox.cli.SandboxSphericalComparisonWorker;
import org.example.roadsimulation.sandbox.execution.SandboxExecutionStore;
import org.example.roadsimulation.sandbox.management.SandboxManagementSettings;
import org.example.roadsimulation.sandbox.management.SandboxOptimizationJobStore;
import org.example.roadsimulation.sandbox.run.SandboxAlgorithmProfile;
import org.example.roadsimulation.sandbox.run.SandboxAlgorithmProfiles;
import org.example.roadsimulation.sandbox.run.SandboxRunSpecificationRevisionV2;
import org.example.roadsimulation.sandbox.run.SandboxRunSpecificationStore;
import org.example.roadsimulation.sandbox.run.SandboxRunSpecificationV1;
import org.example.roadsimulation.sandbox.run.SandboxRunSpecificationV2;
import org.example.roadsimulation.sandbox.workspace.SandboxWorkspaceException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Acquires a compact starting plan from the sandbox's verified spherical execution history. */
final class HeuristicSeedResolver {
    static final String VERSION = "sandbox-heuristic-seed/v1";
    private static final String STRATEGY = "HEURISTIC";
    private static final String PROFILE_ID = SandboxAlgorithmProfiles.HEURISTIC_V1;
    private final SandboxManagementSettings settings;
    private final ObjectMapper json;
    private final SandboxExecutionStore history;
    private final SandboxRunSpecificationRevisionV2 reference;
    private final PlanExecutionAuthentication.FrozenProblem frozen;
    private final SandboxAlgorithmProfile profile = new SandboxAlgorithmProfiles().resolve(STRATEGY, PROFILE_ID);
    private final String commonInput;
    private final String cacheKey;

    record Seed(String artifactVersion, String cacheKey, String sourceExecutionId, String sourceManifestSha256,
                String sourceLastTickSha256, String planFingerprint, GlobalPlan plan) {
        Seed {
            ContractChecks.version(artifactVersion, VERSION);
            ContractChecks.hash(cacheKey, "seed cache key");
            if (!UUID.fromString(ContractChecks.text(sourceExecutionId, "source execution ID")).toString().equals(sourceExecutionId))
                throw new IllegalArgumentException("Invalid seed source execution ID");
            ContractChecks.hash(sourceManifestSha256, "source manifest");
            ContractChecks.hash(sourceLastTickSha256, "source last tick");
            if (plan == null || !plan.fingerprint().equals(planFingerprint))
                throw new IllegalArgumentException("HEURISTIC seed plan fingerprint differs");
        }
    }
    enum Origin { CACHE, LEDGER, RUN }
    record Acquisition(Origin origin, Seed seed) {}

    private final SandboxOptimizationJobStore job;

    HeuristicSeedResolver(SandboxManagementSettings settings, ObjectMapper json,
                          SandboxRunSpecificationRevisionV2 reference, PlanExecutionAuthentication.FrozenProblem frozen,
                          SandboxOptimizationJobStore job) {
        this.job = job;
        this.settings = settings;
        this.json = json;
        this.reference = reference;
        this.frozen = frozen;
        history = new SandboxExecutionStore(settings.url(), settings.user(), settings.password(), json);
        commonInput = SphericalComparisonRun.commonIdentity(reference, frozen.problem(), json);
        cacheKey = OptimizationJson.fingerprint(List.of(VERSION, SphericalComparisonRun.VERSION, settings.url(),
                commonInput, profile, frozen.problem().problem().problemFingerprint()));
    }

    Acquisition acquire(Path cache, Path baseline, Path output, long timeoutSeconds) throws IOException {
        Files.createDirectories(cache);
        Path stored = cache.resolve(cacheKey + ".json");
        try (var channel = FileChannel.open(cache.resolve(cacheKey + ".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var ignored = lock(channel)) {
            var saved = readCache(stored);
            if (saved != null) return new Acquisition(Origin.CACHE, saved);
            for (String id : history.findHeuristicComparisons(commonInput, PROFILE_ID)) {
                Seed seed;
                try {
                    seed = fromVerifiedJournal(id);
                } catch (SandboxWorkspaceException failure) {
                    if (!Set.of("EXECUTION_NOT_FOUND", "EXECUTION_RECORD_CORRUPT").contains(failure.errorCode())) throw failure;
                    continue;
                } catch (IllegalArgumentException incompatibleTrace) {
                    continue;
                }
                save(stored, seed);
                return new Acquisition(Origin.LEDGER, seed);
            }
            var seed = run(baseline, output.resolve("heuristic-seed-run"), timeoutSeconds);
            save(stored, seed);
            return new Acquisition(Origin.RUN, seed);
        }
    }

    private Seed readCache(Path path) throws IOException {
        try {
            var seed = OptimizationJson.read(Files.readString(path), Seed.class);
            if (!cacheKey.equals(seed.cacheKey())) return null;
            validate(seed.plan());
            return seed;
        } catch (NoSuchFileException | IllegalArgumentException missingOrInvalid) {
            return null;
        }
    }

    private static void save(Path path, Seed seed) throws IOException {
        Path temp = Files.createTempFile(path.getParent(), "heuristic-seed-", ".tmp");
        try {
            Files.writeString(temp, OptimizationJson.write(seed));
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static FileLock lock(FileChannel channel) throws IOException {
        try {
            var lock = channel.tryLock();
            if (lock != null) return lock;
        } catch (OverlappingFileLockException busy) {
            // Same-JVM callers contend for the same cache key as separate processes.
        }
        throw new SandboxWorkspaceException("HEURISTIC_SEED_BUSY", "Another search is resolving this HEURISTIC seed");
    }

    private Seed fromVerifiedJournal(String id) {
        var trace = json.createArrayNode();
        var journal = history.verifyJournal(id, tick -> {
            var row = trace.addObject();
            row.set("tick", json.valueToTree(tick.tick()));
            var database = row.putObject("facts").putObject("database");
            var facts = tick.facts().path("database");
            for (var table : List.of("assignment", "assignment_nodes", "shipment_item")) {
                database.set(table, facts.path(table));
            }
        });
        if (journal.lastTick() != null) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) trace.get(trace.size() - 1).path("facts"))
                    .set("database", journal.lastTick().facts().path("database"));
        }
        return fromVerifiedTrace(journal.metadata(), trace);
    }

    private Seed fromVerifiedTrace(JsonNode metadata, JsonNode ticks) {
        var problem = frozen.problem();
        var manifest = metadata.path("manifest");
        int loops = problem.problem().totalLoops();
        if (!"VERIFIED".equals(metadata.path("integrity").asText()) || !"COMPLETED".equals(metadata.path("status").asText())
                || !SphericalComparisonRun.MODE.equals(manifest.path("executionMode").asText())
                || metadata.path("completedLoops").asInt(-1) != loops || !ticks.isArray() || ticks.size() != loops)
            throw new IllegalArgumentException("HEURISTIC seed needs a verified complete spherical execution");
        var source = json.convertValue(manifest.path("sphericalComparison"), SphericalComparisonRun.class);
        var sourceProfile = json.convertValue(manifest.path("runRevision").path("algorithmProfile"), SandboxAlgorithmProfile.class);
        if (!commonInput.equals(source.commonInputFingerprint()) || !problem.paths().equals(source.paths())
                || !OptimizationJson.fingerprint(profile).equals(OptimizationJson.fingerprint(sourceProfile)))
            throw new IllegalArgumentException("HEURISTIC seed execution does not match the frozen inputs or profile");
        var lastDatabase = ticks.get(loops - 1).path("facts").path("database");
        var mapping = SphericalComparisonEvidence.mapping(problem.catalog(), lastDatabase,
                manifest.path("preparation").path("deterministicSimulationRunId").asText());
        var plan = HeuristicTracePlan.convert(problem, manifest, ticks, mapping).plan();
        validate(plan);
        return new Seed(VERSION, cacheKey, metadata.path("executionId").asText(), metadata.path("manifestSha256").asText(),
                metadata.path("lastTickSha256").asText(), plan.fingerprint(), plan);
    }

    private void validate(GlobalPlan plan) {
        new GlobalPlanValidator().validatePlan(frozen.problem(), plan, frozen.data());
    }

    private Seed run(Path baseline, Path directory, long timeoutSeconds) throws IOException {
        var build = EvaluationBuildIdentity.capture(SandboxSphericalComparisonWorker.class);
        var revision = heuristicRevision(baseline);
        var comparison = new SphericalComparisonRun(SphericalComparisonRun.VERSION, commonInput, frozen.problem().paths(), build);
        var work = new SandboxSphericalComparisonWorker.Work(revision.runSpecKey(), revision.revision(),
                comparison, EvaluationBuildIdentity.fileSha256(baseline),
                job == null ? null : new SandboxOptimizationJobStore.Child(job.jobId(), "HEURISTIC_SOURCE"));
        try {
            var receipt = SandboxSphericalComparisonWorker.launch(settings, work, baseline, directory, timeoutSeconds);
            var seed = fromVerifiedJournal(receipt.executionId());
            if (job != null) job.verified(receipt.executionId(), seed.sourceManifestSha256(), seed.sourceLastTickSha256());
            return seed;
        } catch (java.sql.SQLException failure) {
            throw new IOException("Cannot record verified seed source", failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("HEURISTIC seed acquisition interrupted");
        }
    }

    private SandboxRunSpecificationRevisionV2 heuristicRevision(Path baseline) throws IOException {
        var source = reference.specification();
        if (STRATEGY.equals(source.dispatch().strategy()) && PROFILE_ID.equals(source.dispatch().algorithmProfileId()))
            return reference;

        String key = "heuristic-seed-" + OptimizationJson.fingerprint(List.of(commonInput, profile));
        var specification = new SandboxRunSpecificationV2(source.artifactVersion(), key, "HEURISTIC seed " + key.substring(15, 27),
                "Derived for full-horizon search", source.scenario(), source.simulationClock(), source.demand(),
                new SandboxRunSpecificationV1.Dispatch(STRATEGY, source.dispatch().dispatchIntervalLoops(), PROFILE_ID),
                source.weather(), source.events(), source.vehicleInitialization(), source.driverBehavior(), source.random());
        var runs = new SandboxRunSpecificationStore(settings.url(), settings.user(), settings.password(), json);
        var baselineResource = new FileSystemResource(baseline);
        var draft = new ByteArrayResource(json.writeValueAsBytes(specification));
        String expectedHash = runs.compileV2(baselineResource, draft).runSpecificationSha256();
        try {
            var existing = runs.loadRevisionV2(key, 1);
            if (!expectedHash.equals(existing.fingerprints().runSpecificationSha256()))
                throw new IllegalArgumentException("Derived HEURISTIC run key already names a different revision");
            return existing;
        } catch (SandboxWorkspaceException missing) {
            if (!"RUN_SPEC_REVISION_NOT_FOUND".equals(missing.errorCode())) throw missing;
        }
        try {
            if (job == null) runs.saveDraftV2(baselineResource, draft, -1);
            else runs.saveInternalDraftV2(baselineResource, draft);
        } catch (SandboxWorkspaceException concurrent) {
            if (!"DRAFT_CONFLICT".equals(concurrent.errorCode())) throw concurrent;
        }
        // A concurrent writer can publish the same immutable revision. A changed draft must fail the guard.
        var published = runs.publishV2(baselineResource, key, 0L, expectedHash);
        return runs.loadRevisionV2(key, published.revision());
    }
}
