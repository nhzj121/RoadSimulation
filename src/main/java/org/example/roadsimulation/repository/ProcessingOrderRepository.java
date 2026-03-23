package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.ProcessingOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProcessingOrderRepository extends JpaRepository<ProcessingOrder, Long> {
    
    Optional<ProcessingOrder> findByOrderNo(String orderNo);
    
    List<ProcessingOrder> findByProcessingChainId(Long chainId);
    
    List<ProcessingOrder> findByStatus(ProcessingOrder.OrderStatus status);
    
    List<ProcessingOrder> findByProcessingChainIdAndStatus(Long chainId, ProcessingOrder.OrderStatus status);
}
