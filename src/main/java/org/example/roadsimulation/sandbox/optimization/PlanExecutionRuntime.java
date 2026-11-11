package org.example.roadsimulation.sandbox.optimization;

import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.sandbox.baseline.SandboxBaselinePackageV1;
import org.example.roadsimulation.service.TransportLegMetricProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/** Inactive in ordinary sandbox runs; installed once by the authenticated plan execution owner. */
@Component @Profile("sandbox-runtime")
public class PlanExecutionRuntime implements TransportLegMetricProvider {
    private PlanExecutionState state;
    private GlobalPlanValidator.Validation validation;
    private SphericalPathModel paths;
    private String comparisonFingerprint;
    private boolean comparisonPathFailed;
    private Map<String,SphericalPathModel.Endpoint> endpoints;
    public synchronized void install(PlanExecutionTask task,SandboxBaselinePackageV1.Data data) {
        if(paths!=null) throw new IllegalStateException("An execution path model may be installed only once");
        var checked=new GlobalPlanValidator().validate(task,data);
        if(task.compiledProblem().paths().configuration().speedKph().compareTo(new BigDecimal("40"))!=0)
            throw new IllegalArgumentException("G2 v1 requires explicit 40 km/h");
        validation=checked;
        installPaths(task.compiledProblem().paths());
        state=new PlanExecutionState(task);
    }
    public synchronized void installComparison(SphericalComparisonRun comparison) {
        if (paths != null) throw new IllegalStateException("An execution path model may be installed only once");
        installPaths(comparison.paths());
        comparisonFingerprint = comparison.fingerprint();
    }
    private void installPaths(SphericalPathModel model) {
        paths = model;
        endpoints = new TreeMap<>();
        paths.endpoints().forEach(endpoint -> endpoints.put(endpoint.key(), endpoint));
    }
    public synchronized String comparisonFingerprint() { return comparisonFingerprint; }
    public synchronized void requireComparisonPathsHealthy() {
        if (comparisonFingerprint == null || comparisonPathFailed)
            throw new IllegalStateException("SPHERICAL_COMPARISON_PATH_FAILED");
    }
    @Override public synchronized boolean metricsActive() { return paths != null; }
    public synchronized boolean active() { return state!=null; }
    public synchronized PlanExecutionState state() { if(state==null) throw new IllegalStateException("No plan installed"); return state; }
    public GlobalPlanValidator.Validation validation() { state(); return validation; }
    public boolean canReplace(long assignmentId,Vehicle candidate,double requiredLoad,double requiredVolume) {
        if(!active()) return true;
        var trip=state.forAssignment(assignmentId).orElseThrow(() -> new IllegalStateException("Replacement for untracked plan assignment"));
        if(state.hasRemaining(candidate.getId()) || candidate.getMaxLoadCapacityTonnes()==null || candidate.getCargoVolume()==null) return false;
        double weight=requiredLoad,volume=requiredVolume;
        var owner=state.task().plan().vehicles().stream().filter(v -> v.vehicleId()==trip.logicalVehicleId()).findFirst().orElseThrow();
        for(int i=trip.tripIndex()+1;i<owner.trips().size();i++) {
            var peak=validation.peaks().get(new TripKey(owner.vehicleId(),i));
            weight=Math.max(weight,peak.weight().doubleValue()); volume=Math.max(volume,peak.volume().doubleValue());
        }
        return candidate.getMaxLoadCapacityTonnes()>=weight && candidate.getCargoVolume()>=volume;
    }
    public void handoff(long assignment,long original,long spare,int loop,LocalDateTime at) {
        if(active()) state.handoff(assignment,original,spare,loop,at);
    }
    public synchronized Metric metric(Long fromPoi,BigDecimal fromLon,BigDecimal fromLat,Long toPoi,BigDecimal toLon,BigDecimal toLat) {
        if (paths == null) throw new IllegalStateException("No execution path model installed");
        try {
            var from=endpoint(fromPoi,fromLon,fromLat); var to=endpoint(toPoi,toLon,toLat);
            var leg=paths.between(from.key(),to.key());
            return new Metric(leg.distanceMeters().doubleValue(),leg.drivingSeconds());
        } catch (RuntimeException failure) {
            if (comparisonFingerprint != null) comparisonPathFailed = true;
            throw failure;
        }
    }
    private SphericalPathModel.Endpoint endpoint(Long poi,BigDecimal lon,BigDecimal lat) {
        if(lon==null || lat==null) throw new IllegalStateException("PLAN_PATH_COORDINATE_MISSING");
        var known=poi==null?null:endpoints.get("POI:"+poi);
        if(known!=null) {
            if(known.longitude().compareTo(lon)!=0 || known.latitude().compareTo(lat)!=0)
                throw new IllegalStateException("PLAN_PATH_COORDINATE_MISMATCH");
            return known;
        }
        if (poi != null && comparisonFingerprint != null)
            throw new IllegalStateException("PLAN_PATH_ENDPOINT_MISSING");
        return endpoints.values().stream().filter(e -> e.longitude().compareTo(lon)==0 && e.latitude().compareTo(lat)==0).findFirst()
                .orElseThrow(() -> new IllegalStateException("PLAN_PATH_ENDPOINT_MISSING"));
    }
}
