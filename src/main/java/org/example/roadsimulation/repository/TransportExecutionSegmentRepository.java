package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.TransportExecutionSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TransportExecutionSegmentRepository extends JpaRepository<TransportExecutionSegment,Long> {
    List<TransportExecutionSegment> findByRunIdOrderByLegIdAscLoopIndexAscFragmentIndexAsc(String runId);
    List<TransportExecutionSegment> findByLegIdOrderByLoopIndexAscFragmentIndexAsc(Long legId);
    List<TransportExecutionSegment> findByVehicleId(Long vehicleId);
}
