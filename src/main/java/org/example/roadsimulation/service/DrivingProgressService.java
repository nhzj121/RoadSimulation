package org.example.roadsimulation.service;

import org.example.roadsimulation.core.SimulationContext;
import org.example.roadsimulation.core.SimulationModeGuard;
import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

/** Remaining normal driving work, integrated against simulation-time environment segments. */
@Service
public class DrivingProgressService {
    private final DrivingProgressRepository repository;
    private final VehicleRepository vehicles;
    private final TransportRandomEventRepository events;
    private final WeatherEnvironmentService weather;
    private final SimulationContext clock;
    private final SimulationModeGuard mode;
    public DrivingProgressService(DrivingProgressRepository repository, VehicleRepository vehicles,
            TransportRandomEventRepository events,WeatherEnvironmentService weather,SimulationContext clock,SimulationModeGuard mode) {
        this.repository=repository;this.vehicles=vehicles;this.events=events;this.weather=weather;this.clock=clock;this.mode=mode;
    }
    public boolean enabled(){return weather.runId() != null && !mode.isDispatchComparisonExperimentActive();}
    public static boolean driving(Vehicle.VehicleStatus status){return status==Vehicle.VehicleStatus.ORDER_DRIVING || status==Vehicle.VehicleStatus.TRANSPORT_DRIVING;}

    @Transactional public void tick(LocalDateTime now) {
        if(!enabled())return;
        for(Vehicle v:vehicles.findAll()) {
            v=vehicles.findByIdForUpdate(v.getId()).orElse(null);
            if(v!=null)settle(v,now);
        }
    }
    public Integer legIndex(Assignment a){
        if(a==null || a.getNodes()==null || a.getNodes().isEmpty())return null;
        AssignmentNode pending=a.getNextPendingNode();return pending==null?null:pending.getSequenceIndex();
    }
    @Transactional public DrivingProgress settle(Vehicle vehicle,LocalDateTime now) {
        if(!enabled() || vehicle==null || vehicle.getCurrentAssignment()==null)return null;
        Assignment a=vehicle.getCurrentAssignment();
        if(a.getStatus()!=Assignment.AssignmentStatus.ASSIGNED && a.getStatus()!=Assignment.AssignmentStatus.IN_PROGRESS)return null;
        DrivingProgress p;
        if(vehicle.getCurrentStatus()==Vehicle.VehicleStatus.BREAKDOWN) {
            p=repository.findFirstByVehicleIdOrderByPhaseStartDesc(vehicle.getId()).orElse(null);
            if(p==null || !Objects.equals(p.getAssignmentId(),a.getId()) || !Objects.equals(p.getRunId(), weather.runId()))return null;
        } else {
            if(!driving(vehicle.getCurrentStatus()))return null;
            LocalDateTime start=vehicle.getStatusStartTime()==null?now:vehicle.getStatusStartTime();
            String key=weather.runId()+":"+a.getId()+":"+vehicle.getCurrentStatus()+":"+legIndex(a)+":"+start;
            p=repository.findById(key).orElse(null);
            if(p==null){
                p=new DrivingProgress();p.setPhaseKey(key);p.setRunId(weather.runId());p.setAssignmentId(a.getId());p.setVehicleId(vehicle.getId());
                p.setLegIndex(legIndex(a));p.setDrivingStatus(vehicle.getCurrentStatus());p.setPhaseStart(start);p.setLastSettledTime(start);
                double work=vehicle.getStatusDuration()==null?1800:Math.max(0,vehicle.getStatusDuration().getSeconds());
                p.setInitialWorkSeconds(work);p.setRemainingWorkSeconds(work);
            }
        }
        if(now.isAfter(p.getLastSettledTime()) && p.getRemainingWorkSeconds()>0) {
            LocalDateTime from=p.getLastSettledTime();
            List<TransportRandomEvent> impacts=events.findByVehicleId(vehicle.getId()).stream()
                .filter(e->Objects.equals(e.getRunId(), weather.runId()) && Objects.equals(e.getAssignmentId(), a.getId()))
                .filter(e->e.getStartTime().isBefore(now)&&e.getPlannedEndTime().isAfter(from)).toList();
            TreeSet<LocalDateTime> points=new TreeSet<>();points.add(from);points.add(now);points.addAll(weather.boundaries(from,now));
            for(var e:impacts){if(e.getStartTime().isAfter(from)&&e.getStartTime().isBefore(now))points.add(e.getStartTime());
                if(e.getPlannedEndTime().isAfter(from)&&e.getPlannedEndTime().isBefore(now))points.add(e.getPlannedEndTime());}
            List<LocalDateTime> ordered=new ArrayList<>(points);
            for(int i=0;i<ordered.size()-1 && p.getRemainingWorkSeconds()>0;i++) {
                LocalDateTime begin=ordered.get(i),end=ordered.get(i+1);
                double factor=weather.at(begin).speedFactor();
                for(var e:impacts)if(!begin.isBefore(e.getStartTime()) && begin.isBefore(e.getPlannedEndTime()))factor*=e.getSpeedFactor();
                integrate(p,begin,end,factor);
            }
        }
        p.setLastSettledTime(now.isAfter(p.getLastSettledTime())?now:p.getLastSettledTime());
        if(p.getRemainingWorkSeconds()<=1e-7 && p.getObservedCompletedTime()==null){
            p.setRemainingWorkSeconds(0);p.setObservedCompletedTime(now);
            if(p.getModelCompletedTime()==null)p.setModelCompletedTime(p.getPhaseStart());
        }
        return repository.save(p);
    }
    /** Pure integration used by normal loop and reusable by sandbox callers. */
    public static void integrate(DrivingProgress p,LocalDateTime from,LocalDateTime to,double factor) {
        if(!Double.isFinite(factor)||factor<0||factor>1)throw new IllegalArgumentException("factor in [0,1]");
        double seconds=Math.max(0,Duration.between(from,to).toNanos()/1e9);
        double used=factor==0?seconds:Math.min(seconds,p.getRemainingWorkSeconds()/factor);
        if(factor<1){p.setAffectedSeconds(p.getAffectedSeconds()+used);p.setLostWorkSeconds(p.getLostWorkSeconds()+used*(1-factor));}
        p.setRemainingWorkSeconds(Math.max(0,p.getRemainingWorkSeconds()-used*factor));
        if(p.getRemainingWorkSeconds()<1e-7 && p.getModelCompletedTime()==null)p.setModelCompletedTime(from.plusNanos(Math.round(used*1e9)));
    }
    @Transactional public boolean canAdvance(Vehicle v,LocalDateTime now) {
        if(!enabled())return true;
        DrivingProgress p=settle(v,now);
        return v.getCurrentStatus()!=Vehicle.VehicleStatus.BREAKDOWN && (p==null || p.getRemainingWorkSeconds()<=1e-7);
    }
    @Transactional(readOnly=true) public DrivingProgress latest(Long vehicleId){return repository.findFirstByVehicleIdOrderByPhaseStartDesc(vehicleId).orElse(null);}
    public double effectiveFactor(Long vehicleId,LocalDateTime now) {
        double factor=weather.at(now).speedFactor();
        for(var e:events.findByVehicleId(vehicleId))if(Objects.equals(e.getRunId(), weather.runId()) && !now.isBefore(e.getStartTime())&&now.isBefore(e.getPlannedEndTime()))factor*=e.getSpeedFactor();
        return factor;
    }
    public LocalDateTime now(){return clock.getCurrentSimTime();}
}
