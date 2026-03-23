package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.ProcessingChain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProcessingChainRepository extends JpaRepository<ProcessingChain, Long> {
    
    Optional<ProcessingChain> findByChainCode(String chainCode);
    
    List<ProcessingChain> findByStatus(ProcessingChain.ChainStatus status);
    
    boolean existsByChainCode(String chainCode);
}
