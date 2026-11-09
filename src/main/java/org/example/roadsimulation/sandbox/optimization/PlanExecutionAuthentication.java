package org.example.roadsimulation.sandbox.optimization;

import com.fasterxml.jackson.databind.*;
import org.example.roadsimulation.sandbox.baseline.*;
import org.example.roadsimulation.sandbox.scenario.definition.*;
import org.example.roadsimulation.sandbox.run.*;
import org.springframework.core.io.Resource;

/** Rebuilds G1 from the frozen source, not from caller-supplied hashes. */
public final class PlanExecutionAuthentication {
    private PlanExecutionAuthentication() {}
    public record FrozenProblem(CompiledOptimizationProblem problem, SandboxBaselinePackageV1.Data data) {}

    public static SandboxBaselinePackageV1.Data verify(PlanExecutionTask task,Resource source,
            SandboxScenarioRevisionV1 scene,SandboxRunSpecificationRevisionV2 revision) {
        if(!task.runSpecKey().equals(revision.runSpecKey())||task.runSpecRevision()!=revision.revision())
            throw new IllegalArgumentException("PLAN_RUN_REFERENCE_MISMATCH");
        var frozen = compile(source, scene, revision, task.compiledProblem().paths().configuration(),
                task.compiledProblem().problem().evaluationContractVersion());
        if(!frozen.problem().equals(task.compiledProblem()))throw new IllegalArgumentException("PLAN_FROZEN_INPUT_MISMATCH");
        new GlobalPlanValidator().validate(task,frozen.data());
        return frozen.data();
    }

    public static FrozenProblem compile(Resource source, SandboxScenarioRevisionV1 scene,
            SandboxRunSpecificationRevisionV2 revision, SphericalPathModel.Configuration paths, String evaluationVersion) {
        var json=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var baseline=new SandboxBaselineLoader(json).load(source);
        var scenario=new SandboxScenarioCompiler(json).compile(baseline,scene.definition());
        var run=new SandboxRunCompilerV2(json).compile(revision.specification(),scene,scenario);
        if(!run.runSpecificationSha256().equals(revision.fingerprints().runSpecificationSha256())
                ||!run.preparedRunFactsSha256().equals(revision.fingerprints().preparedRunFactsSha256()))
            throw new IllegalArgumentException("PLAN_SOURCE_RUN_MISMATCH");
        var compiled=new OptimizationProblemCompiler().compile(baseline,scene,scenario,run,
                paths,evaluationVersion);
        return new FrozenProblem(compiled,scenario.effectiveData().data());
    }
}
