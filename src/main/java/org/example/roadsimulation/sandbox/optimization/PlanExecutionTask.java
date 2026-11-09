package org.example.roadsimulation.sandbox.optimization;

import java.util.Objects;

public record PlanExecutionTask(String artifactVersion, String runSpecKey, int runSpecRevision,
        CompiledOptimizationProblem compiledProblem, GlobalPlan plan, String decoderVersion, String repairVersion) {
    public static final String VERSION = "sandbox-plan-execution-task/v1";
    public static final String DECODER = "whole-trip-ready/v1";
    public static final String REPAIR = "unassigned-spare-inherit/v1";
    public PlanExecutionTask {
        artifactVersion = ContractChecks.version(artifactVersion, VERSION);
        runSpecKey = ContractChecks.text(runSpecKey, "runSpecKey");
        if (runSpecRevision <= 0) throw new IllegalArgumentException("Invalid run revision");
        Objects.requireNonNull(compiledProblem); Objects.requireNonNull(plan);
        if(compiledProblem.paths().configuration().speedKph().compareTo(new java.math.BigDecimal("40"))!=0)
            throw new IllegalArgumentException("G2 v1 requires explicit 40 km/h");
        decoderVersion = ContractChecks.version(decoderVersion, DECODER);
        repairVersion = ContractChecks.version(repairVersion, REPAIR);
        if (!plan.problem().equals(compiledProblem.problem())) throw new IllegalArgumentException("Plan/problem mismatch");
        plan.validateCatalog(compiledProblem.catalog().taskKeys());
    }
    public String fingerprint() { return OptimizationJson.fingerprint(this); }
}
