package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.TransportRandomEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** Read-only combination of frozen weather and persisted event intervals for one owner. */
public final class TransportImpactWindows {
    private TransportImpactWindows() {}
    public static List<WeatherDrivingIntegrator.Window> combine(
            List<WeatherDrivingIntegrator.Window> weather,List<TransportRandomEvent> events,
            String runId,Long assignmentId,Long vehicleId){
        if(weather==null||weather.isEmpty())throw new IllegalArgumentException("Missing weather window");
        LocalDateTime start=weather.get(0).from(),end=weather.get(weather.size()-1).to();
        List<TransportRandomEvent> matching=events.stream()
                .filter(e->Objects.equals(runId,e.getRunId())&&Objects.equals(assignmentId,e.getAssignmentId())
                        &&(Objects.equals(vehicleId,e.getVehicleId())
                            || (Objects.equals(vehicleId,e.getReplacementVehicleId()) && "REPLACED".equals(e.getReplacementOutcome()))))
                .filter(e->e.getStartTime()!=null&&e.getStartTime().isBefore(end))
                .sorted(Comparator.comparing(TransportRandomEvent::getStartTime)
                        .thenComparing(TransportRandomEvent::getId,Comparator.nullsLast(Long::compareTo))).toList();
        TreeSet<LocalDateTime> boundaries=new TreeSet<>();boundaries.add(start);boundaries.add(end);
        weather.forEach(w->{boundaries.add(w.from());boundaries.add(w.to());});
        for(var event:matching){
            if(event.getStartTime().isAfter(start))boundaries.add(event.getStartTime());
            var finish=finish(event);
            if(finish!=null&&finish.isAfter(start)&&finish.isBefore(end))boundaries.add(finish);
        }
        List<LocalDateTime> points=new ArrayList<>(boundaries);
        List<WeatherDrivingIntegrator.Window> result=new ArrayList<>();
        for(int i=0;i<points.size()-1;i++){
            LocalDateTime from=points.get(i),to=points.get(i+1);
            double factor=weather.stream().filter(w->!from.isBefore(w.from())&&from.isBefore(w.to()))
                    .findFirst().orElseThrow(()->new IllegalArgumentException("Weather gap")).speedFactor();
            for(var event:matching){
                var finish=finish(event);
                if(!from.isBefore(event.getStartTime())&&(finish==null||from.isBefore(finish))){
                    if(event.getSpeedFactor()==null||!Double.isFinite(event.getSpeedFactor())
                            ||event.getSpeedFactor()<0||event.getSpeedFactor()>1)
                        throw new IllegalArgumentException("Invalid persisted event speed factor");
                    // New owner cannot move before the successful handoff timestamp.
                    factor*=Objects.equals(vehicleId,event.getReplacementVehicleId()) ? 0 : event.getSpeedFactor();
                }
            }
            result.add(new WeatherDrivingIntegrator.Window(from,to,factor));
        }
        return List.copyOf(result);
    }
    private static LocalDateTime finish(TransportRandomEvent event){
        return event.getResolvedTime()!=null?event.getResolvedTime():event.getPlannedEndTime();
    }
}
