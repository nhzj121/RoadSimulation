package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.TransportRandomEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransportRandomEventRepository extends JpaRepository<TransportRandomEvent, Long> {
    List<TransportRandomEvent> findByRunId(String runId);
    List<TransportRandomEvent> findByVehicleId(Long vehicleId);
    Optional<TransportRandomEvent> findFirstByVehicleIdAndStatus(
            Long vehicleId,
            TransportRandomEvent.EventStatus status
    );

    List<TransportRandomEvent> findByStatus(TransportRandomEvent.EventStatus status);

    List<TransportRandomEvent> findAllByOrderByStartTimeDesc(Pageable pageable);
}
