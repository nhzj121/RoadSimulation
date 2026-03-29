package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.ProcessingChain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProcessingChainRepository extends JpaRepository<ProcessingChain, Long> {

    Optional<ProcessingChain> findByChainCode(String chainCode);

    List<ProcessingChain> findByStatus(ProcessingChain.ChainStatus status);

    boolean existsByChainCode(String chainCode);

    /**
     * 查找所有以前驱加工链 ID 为条件的合并加工链
     * @param predecessorChainId 前驱加工链 ID
     * @return 合并加工链列表
     */
    @Query("SELECT pc FROM ProcessingChain pc JOIN pc.predecessorChainIds pId WHERE pId = :predecessorChainId")
    List<ProcessingChain> findByPredecessorChainId(@Param("predecessorChainId") Long predecessorChainId);

    /**
     * 查找所有合并加工链（有前驱的加工链）
     * @return 合并加工链列表
     */
    @Query("SELECT pc FROM ProcessingChain pc WHERE SIZE(pc.predecessorChainIds) > 0")
    List<ProcessingChain> findAllMergeChains();
}
