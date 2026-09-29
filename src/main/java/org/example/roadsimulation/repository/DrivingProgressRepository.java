package org.example.roadsimulation.repository;
import org.example.roadsimulation.entity.DrivingProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface DrivingProgressRepository extends JpaRepository<DrivingProgress, String> {
    Optional<DrivingProgress> findFirstByVehicleIdOrderByPhaseStartDesc(Long vehicleId);
    List<DrivingProgress> findByRunId(String runId);
}
