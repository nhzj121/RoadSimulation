package org.example.roadsimulation.sandbox.optimization;

import java.util.Map;
import java.util.Objects;

public record CompiledOptimizationProblem(String artifactVersion, OptimizationInputManifest input,
        ProductionTaskCatalog catalog, ProductionDependencies dependencies, SphericalPathModel paths,
        OptimizationProblemRef problem, String sourceScenarioSha256, String sourceRunSha256) {
    public static final String VERSION = "sandbox-compiled-optimization-problem/v1";
    public CompiledOptimizationProblem {
        artifactVersion = ContractChecks.version(artifactVersion, VERSION);
        Objects.requireNonNull(input); Objects.requireNonNull(catalog); Objects.requireNonNull(dependencies);
        Objects.requireNonNull(paths); Objects.requireNonNull(problem);
        sourceScenarioSha256 = ContractChecks.hash(sourceScenarioSha256, "sourceScenarioSha256");
        sourceRunSha256 = ContractChecks.hash(sourceRunSha256, "sourceRunSha256");
        if (!catalog.inputFingerprint().equals(input.fingerprint()) || catalog.totalLoops() != input.totalLoops()
                || !dependencies.equals(ProductionDependencies.from(catalog))
                || !problem.equals(reference(input, catalog, dependencies, paths)))
            throw new IllegalArgumentException("Compiled problem identity/dependencies mismatch");
        for (var flow : catalog.flows()) {
            paths.between("POI:" + flow.originPoiId(), "POI:" + flow.destinationPoiId());
        }
    }
    public static OptimizationProblemRef reference(OptimizationInputManifest input, ProductionTaskCatalog catalog,
            ProductionDependencies dependencies, SphericalPathModel paths) {
        String hash = OptimizationJson.fingerprint(Map.of("artifactVersion", VERSION, "input", input.fingerprint(),
                "catalog", catalog.fingerprint(), "dependencies", dependencies.fingerprint(), "paths", paths.fingerprint()));
        return new OptimizationProblemRef(hash, input.simulationStart(), input.totalLoops(), input.tickSeconds(), input.evaluationContractVersion());
    }
}
