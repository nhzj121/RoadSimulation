package org.example.roadsimulation.sandbox.optimization;

import java.util.List;

public record PlanExecutionSummary(String artifactVersion, String executionId, int requestedLoops, int completedLoops,
        PlanExecutionState.Snapshot state, List<GlobalPlan.TaskKey> deferredTasks,
        List<TripKey> unfinishedTrips, List<TripOutcome> trips) {
    public record TripOutcome(TripKey trip,String status,String reason,Long assignmentId,long physicalVehicleId,List<GlobalPlan.TaskKey> tasks) {
        public TripOutcome { tasks=List.copyOf(tasks); }
    }
    public PlanExecutionSummary {
        artifactVersion=ContractChecks.version(artifactVersion,"sandbox-plan-execution-summary/v1");
        if(requestedLoops<=0||completedLoops<0||completedLoops>requestedLoops)throw new IllegalArgumentException("Invalid execution horizon");
        deferredTasks=List.copyOf(deferredTasks); unfinishedTrips=List.copyOf(unfinishedTrips); trips=List.copyOf(trips);
    }
    public static PlanExecutionSummary from(String id,int completed,PlanExecutionState execution) {
        var task=execution.task(); var unfinished=new java.util.ArrayList<TripKey>();var outcomes=new java.util.ArrayList<TripOutcome>();
        var snapshot=execution.snapshot();
        task.plan().vehicles().forEach(v -> { for(int i=0;i<v.trips().size();i++) {
            var key=new TripKey(v.vehicleId(),i); if(!execution.completed(key)) unfinished.add(key);
            var queue=snapshot.queues().stream().filter(q -> q.logicalVehicleId()==v.vehicleId()).findFirst().orElseThrow();
            Long assignment=execution.assignment(key);boolean done=execution.completed(key);
            String reason=done?null:i>queue.nextTripIndex()?"PREVIOUS_TRIP_PENDING":queue.reason();
            String status=done?"COMPLETED":assignment==null?"WAITING":"UNFINISHED";
            if(!done&&assignment!=null&&"IN_PROGRESS".equals(reason)&&completed==task.plan().problem().totalLoops())reason="DEADLINE_DURING_EXECUTION";
            long actual=execution.physical(v.vehicleId());
            for(var event:snapshot.events())if(key.equals(event.trip())&&("DISPATCH".equals(event.type())||"HANDOFF".equals(event.type())))actual=event.toVehicle();
            outcomes.add(new TripOutcome(key,status,reason,assignment,actual,v.trips().get(i).nodes().stream()
                    .filter(n -> n.action()==GlobalPlan.Action.LOAD).map(GlobalPlan.Node::task).toList()));
        }});
        return new PlanExecutionSummary("sandbox-plan-execution-summary/v1",id,task.plan().problem().totalLoops(),completed,
                snapshot,task.plan().deferredTasks(),unfinished,outcomes);
    }
}
