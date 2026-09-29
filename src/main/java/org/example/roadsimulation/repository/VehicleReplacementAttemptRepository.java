package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.VehicleReplacementAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface VehicleReplacementAttemptRepository extends JpaRepository<VehicleReplacementAttempt,Long> {
    List<VehicleReplacementAttempt> findByRunId(String runId);
    List<VehicleReplacementAttempt> findByEventIdOrderByIdAsc(Long eventId);
    Optional<VehicleReplacementAttempt> findFirstByEventIdAndStatusOrderByIdDesc(
            Long eventId, VehicleReplacementAttempt.AttemptStatus status);
}
