package org.example.roadsimulation.dto;

import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.entity.VehicleType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class VehicleMatchingResult {
    private Goods goods;
    private VehicleMatchingRequest request;
    private List<VehicleMatch> matches;
    private LocalDateTime matchTime;

    public VehicleMatchingResult() {}

    public VehicleMatchingResult(Goods goods, VehicleMatchingRequest request,
                                 List<VehicleScore> scoredVehicles) {
        this.goods = goods;
        this.request = request;
        this.matches = scoredVehicles.stream()
                .map(score -> new VehicleMatch(score.getVehicle(), score.getScore()))
                .collect(Collectors.toList());
        this.matchTime = LocalDateTime.now();
    }

    public VehicleMatch getBestMatch() {
        return matches.isEmpty() ? null : matches.get(0);
    }

    public List<VehicleMatch> getTopMatches(int n) {
        return matches.stream().limit(n).collect(Collectors.toList());
    }

    // Getters and Setters
    public Goods getGoods() { return goods; }
    public void setGoods(Goods goods) { this.goods = goods; }
    public VehicleMatchingRequest getRequest() { return request; }
    public void setRequest(VehicleMatchingRequest request) { this.request = request; }
    public List<VehicleMatch> getMatches() { return matches; }
    public void setMatches(List<VehicleMatch> matches) { this.matches = matches; }
    public LocalDateTime getMatchTime() { return matchTime; }
    public void setMatchTime(LocalDateTime matchTime) { this.matchTime = matchTime; }

    public static class VehicleMatch {
        private Vehicle vehicle;
        private double matchScore;
        private String matchLevel;

        public VehicleMatch() {}

        public VehicleMatch(Vehicle vehicle, double matchScore) {
            this.vehicle = vehicle;
            this.matchScore = matchScore;
            this.matchLevel = calculateMatchLevel(matchScore);
        }

        private String calculateMatchLevel(double score) {
            if (score >= 0.9) return "优秀";
            if (score >= 0.7) return "良好";
            if (score >= 0.5) return "一般";
            return "合格";
        }

        // Getters and Setters
        public Vehicle getVehicle() { return vehicle; }
        public void setVehicle(Vehicle vehicle) { this.vehicle = vehicle; }
        public double getMatchScore() { return matchScore; }
        public void setMatchScore(double matchScore) { this.matchScore = matchScore; }
        public String getMatchLevel() { return matchLevel; }
        public void setMatchLevel(String matchLevel) { this.matchLevel = matchLevel; }
    }

    public static class VehicleScore {
        private final Vehicle vehicle;
        private final double score;

        public VehicleScore(Vehicle vehicle, double score) {
            this.vehicle = vehicle;
            this.score = score;
        }

        public Vehicle getVehicle() { return vehicle; }
        public double getScore() { return score; }
    }
}