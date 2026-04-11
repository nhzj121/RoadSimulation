package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.ProcessingStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProcessingStageRepository extends JpaRepository<ProcessingStage, Long> {
    
    List<ProcessingStage> findByProcessingChainIdOrderByStageOrderAsc(Long chainId);
    
    List<ProcessingStage> findByProcessingPOI_Id(Long poiId);
    
    void deleteByProcessingChainId(Long chainId);
}
