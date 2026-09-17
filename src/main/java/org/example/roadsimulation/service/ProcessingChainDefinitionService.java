package org.example.roadsimulation.service;

import org.example.roadsimulation.dto.ProcessingChainGraphRequest;
import org.example.roadsimulation.dto.ProcessingChainGraphResponse;
import org.example.roadsimulation.entity.ProcessingChain;
import org.example.roadsimulation.entity.ProcessingStage;

import java.util.List;
import java.util.Optional;

/** Manages static processing-chain definitions only. */
public interface ProcessingChainDefinitionService {

    ProcessingChain createChain(ProcessingChain chain);

    ProcessingChainGraphResponse createGraph(ProcessingChainGraphRequest request);

    ProcessingChainGraphResponse getGraph(Long chainId);

    Optional<ProcessingChain> getChainById(Long id);

    List<ProcessingChain> getAllChains();

    List<ProcessingStage> getStages(Long chainId);

    ProcessingChain updateChainStatus(Long id, ProcessingChain.ChainStatus status);

    void deleteChain(Long id);

    ProcessingStage createStage(Long chainId, ProcessingStage stage);

    ProcessingStage updateStage(Long stageId, ProcessingStage stageDetails);

    void deleteStage(Long stageId);
}
