package org.example.roadsimulation.sandbox.optimization;

import org.example.roadsimulation.core.SimulationContext;
import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.repository.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.time.LocalDateTime;
import static org.example.roadsimulation.sandbox.optimization.ProductionTaskCatalog.*;

@Service @Profile("sandbox-runtime")
public class PlanTaskObservationService {
    public record Observation(ProductionTaskMapper.Mapping mapping, PlanExecutionState.Snapshot state) {}
    private final PlanExecutionRuntime runtime;
    private final ProductionPlanRepository plans;
    private final ProductionBatchRepository batches;
    private final ProcessingExecutionFlowRepository flows;
    private final ShipmentItemRepository items;
    private final AssignmentRepository assignments;
    private final SimulationContext clock;
    public PlanTaskObservationService(PlanExecutionRuntime runtime,ProductionPlanRepository plans,ProductionBatchRepository batches,
            ProcessingExecutionFlowRepository flows,ShipmentItemRepository items,AssignmentRepository assignments,SimulationContext clock) {
        this.runtime=runtime;this.plans=plans;this.batches=batches;this.flows=flows;this.items=items;this.assignments=assignments;this.clock=clock;
    }
    @Transactional(readOnly=true)
    public Observation observe(int loop,LocalDateTime at) {
        var state=runtime.state();var catalog=state.task().compiledProblem().catalog();
        var run=clock.getSimulationRunId().orElseThrow();
        var expectedBatches=new HashSet<BatchKey>();catalog.batches().stream().filter(b -> b.key().generationLoop()<=loop).forEach(b -> expectedBatches.add(b.key()));
        var actualBatches=new HashSet<BatchKey>();
        for(var plan:plans.findAll()) if(run.equals(plan.getSimulationRunId())) {
            if(plan.getGenerationRound()==null || plan.getStatus()!=ProductionPlan.PlanStatus.RELEASED
                    ||!actualBatches.add(new BatchKey(plan.getGenerationRound(),plan.getChain().getId())))
                throw new IllegalStateException("PLAN_BATCH_IDENTITY_MISMATCH");
        }
        if(!actualBatches.equals(expectedBatches)) throw new IllegalStateException("PLAN_BATCH_COVERAGE_MISMATCH");
        var expectedFlows=new HashSet<FlowKey>();var observed=new ArrayList<ProductionTaskMapper.ObservedFlow>();
        var structuralFlows=new HashSet<FlowKey>();
        for(var batch:batches.findAll()) if(run.equals(batch.getPlan().getSimulationRunId())) {
            var batchKey=new BatchKey(batch.getPlan().getGenerationRound(),batch.getChain().getId());
            for(var flow:flows.findByBatchId(batch.getId())) {
                var key=new FlowKey(batchKey,flow.getFromExecution().getStage().getId(),flow.getToExecution().getStage().getId(),flow.getInputKey());
                if(!structuralFlows.add(key))throw new IllegalStateException("DUPLICATE_PLAN_FLOW");
                // Expected release is derived from producer completion, independently of shipment existence.
                if(flow.getFromExecution().getStatus()==ProcessingStageExecution.ExecutionStatus.COMPLETED)expectedFlows.add(key);
                if(flow.getShipment()!=null) observed.add(ProductionObservationAdapter.from(flow,items.findByShipmentId(flow.getShipment().getId())));
            }
        }
        var expectedStructure=new HashSet<FlowKey>();catalog.flows().stream().filter(f -> expectedBatches.contains(f.key().batch())).forEach(f -> expectedStructure.add(f.key()));
        if(!structuralFlows.equals(expectedStructure))throw new IllegalStateException("PLAN_FLOW_COVERAGE_MISMATCH");
        var mapping=new ProductionTaskMapper().map(run,catalog,expectedFlows,observed);
        for(var binding:state.snapshot().bindings()) {
            var assignment=assignments.findById(binding.assignmentId()).orElseThrow();
            if(assignment.getStatus()==Assignment.AssignmentStatus.COMPLETED)state.complete(binding.trip(),loop,at);
            else if(assignment.getStatus()==Assignment.AssignmentStatus.FAILED)throw new IllegalStateException("PLAN_ASSIGNMENT_FAILED");
            else if(assignment.getStatus()==Assignment.AssignmentStatus.CANCELLED)state.waitFor(binding.trip(),"TASK_CANCELLED",loop,at);
            else {
                if(assignment.getAssignedVehicle().getId()!=state.physical(binding.trip().logicalVehicleId()))
                    throw new IllegalStateException("PLAN_PHYSICAL_OWNER_MISMATCH");
                var status=assignment.getAssignedVehicle().getCurrentStatus();
                if(status==Vehicle.VehicleStatus.SCRAPPED)state.waitFor(binding.trip(),"WAIT_REPLACEMENT",loop,at);
                else if(status==Vehicle.VehicleStatus.BREAKDOWN)state.waitFor(binding.trip(),"WAIT_REPAIR",loop,at);
                else state.waitFor(binding.trip(),"IN_PROGRESS",loop,at);
            }
        }
        return new Observation(mapping,state.snapshot());
    }
}
