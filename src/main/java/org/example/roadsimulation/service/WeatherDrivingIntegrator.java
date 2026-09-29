package org.example.roadsimulation.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Consumes a contiguous simulation window once; completion discards its unused tail. */
public final class WeatherDrivingIntegrator {
    private WeatherDrivingIntegrator() {}
    public record Window(LocalDateTime from, LocalDateTime to, double speedFactor) {
        public Window {
            if (from == null || to == null || !to.isAfter(from)
                    || !Duration.between(from,to).equals(Duration.ofSeconds(Duration.between(from,to).getSeconds()))
                    || !Double.isFinite(speedFactor) || speedFactor < 0 || speedFactor > 1) {
                throw new IllegalArgumentException("Invalid weather driving window");
            }
        }
        public long seconds() { return Duration.between(from,to).getSeconds(); }
    }
    public record Increment(double previousDistance, double distance, long seconds, double travelTimeFactor,
            LocalDateTime from, LocalDateTime to) {}
    public record Result(double executedDistance, long consumedSeconds, boolean completed,
            List<Increment> increments) {}

    public static Result integrate(double plannedDistance, long plannedSeconds,
            double executedDistance, List<Window> windows) {
        if (!Double.isFinite(plannedDistance) || plannedDistance <= 0 || plannedSeconds <= 0
                || !Double.isFinite(executedDistance) || executedDistance < 0
                || executedDistance > plannedDistance || windows == null || windows.isEmpty()) {
            throw new IllegalArgumentException("Invalid driving facts");
        }
        LocalDateTime previousEnd=null;
        for(var window:windows){
            if(previousEnd!=null&&!previousEnd.equals(window.from()))
                throw new IllegalArgumentException("Weather windows must be contiguous");
            previousEnd=window.to();
        }
        double distance=executedDistance;
        long consumed=0;
        List<Increment> increments=new ArrayList<>();
        for(var window:windows){
            if(distance==plannedDistance)break;
            if(window.speedFactor()==0){
                consumed=Math.addExact(consumed,window.seconds());
                continue;
            }
            double remainingWork=plannedSeconds*(plannedDistance-distance)/plannedDistance;
            double finishSeconds=remainingWork/window.speedFactor();
            if(!Double.isFinite(finishSeconds)||finishSeconds>Long.MAX_VALUE)
                throw new IllegalArgumentException("Completion exceeds simulation-second range");
            long finishWholeSeconds=(long)Math.ceil(finishSeconds);
            boolean finished=finishWholeSeconds<=window.seconds();
            long used=finished?finishWholeSeconds:window.seconds();
            double next=finished?plannedDistance:Math.min(plannedDistance,
                    distance+plannedDistance*(used*window.speedFactor())/plannedSeconds);
            increments.add(new Increment(distance,next,used,1/window.speedFactor(),window.from(),window.from().plusSeconds(used)));
            distance=next;consumed=Math.addExact(consumed,used);
        }
        return new Result(distance,consumed,distance==plannedDistance,List.copyOf(increments));
    }
}
