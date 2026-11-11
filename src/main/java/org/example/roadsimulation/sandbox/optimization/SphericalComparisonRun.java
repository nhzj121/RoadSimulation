package org.example.roadsimulation.sandbox.optimization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.roadsimulation.evaluation.EvaluationMetricCatalog;
import org.example.roadsimulation.sandbox.run.SandboxRunSpecificationRevisionV2;
import org.example.roadsimulation.sandbox.scenario.definition.SandboxScenarioRevisionV1;
import org.springframework.core.io.Resource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Manifest payload for the sandbox-only HEURISTIC control run. */
public record SphericalComparisonRun(String artifactVersion, String commonInputFingerprint,
                                     SphericalPathModel paths, EvaluationBuildIdentity build) {
    public static final String VERSION = "sandbox-spherical-comparison/v1";
    public static final String MODE = "HEURISTIC_SPHERICAL_COMPARISON";
    static final SphericalPathModel.Configuration CONFIGURATION = new SphericalPathModel.Configuration(
            "SPHERICAL_DIRECT/v1", BigDecimal.valueOf(6371000), BigDecimal.valueOf(40));

    public SphericalComparisonRun {
        ContractChecks.version(artifactVersion, VERSION);
        ContractChecks.hash(commonInputFingerprint, "commonInputFingerprint");
        ContractChecks.hash(Objects.requireNonNull(build).classpathFingerprint(), "classpath fingerprint");
        ContractChecks.hash(build.jdkFingerprint(), "JDK fingerprint");
        if (!CONFIGURATION.equals(Objects.requireNonNull(paths).configuration()))
            throw new IllegalArgumentException("Comparison requires the frozen 40 km/h spherical model");
    }

    public String fingerprint() { return OptimizationJson.fingerprint(this); }

    public void verify(Resource baseline, SandboxScenarioRevisionV1 scene, SandboxRunSpecificationRevisionV2 run,
                       ObjectMapper json) {
        if (!"HEURISTIC".equals(run.specification().dispatch().strategy()))
            throw new IllegalArgumentException("Spherical comparison requires HEURISTIC");
        var problem = compile(baseline, scene, run);
        if (!paths.equals(problem.paths()) || !commonInputFingerprint.equals(commonIdentity(run, problem, json)))
            throw new IllegalArgumentException("Spherical comparison frozen input differs");
    }

    static CompiledOptimizationProblem compile(Resource baseline, SandboxScenarioRevisionV1 scene,
                                               SandboxRunSpecificationRevisionV2 run) {
        return PlanExecutionAuthentication.compile(baseline, scene, run, CONFIGURATION,
                EvaluationMetricCatalog.CONTRACT_VERSION).problem();
    }

    static String commonIdentity(SandboxRunSpecificationRevisionV2 run, CompiledOptimizationProblem problem,
                                 ObjectMapper json) {
        ObjectNode specification = json.valueToTree(run.specification());
        specification.remove(List.of("runSpecKey", "displayName", "description", "dispatch"));
        var catalog = problem.catalog();
        return OptimizationJson.fingerprint(Map.of(
                "specification", specification, "scene", problem.input().scenarioFactsFingerprint(),
                "resolvedVehicles", run.fingerprints().resolvedVehicleInitialStateSha256(),
                "weather", run.fingerprints().weatherTimelineSha256(),
                "events", run.fingerprints().eventConfigurationSha256(),
                "paths", problem.paths().fingerprint(), "evaluation", problem.input().evaluationContractVersion(),
                "catalogContent", List.of(catalog.batches(), catalog.flows(), catalog.tasks()),
                "dispatchIntervalLoops", run.specification().dispatch().dispatchIntervalLoops()));
    }
}
