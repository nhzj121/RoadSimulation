package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.POI;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * POI Repository
 *
 * 功能：
 * 1. 提供 CRUD 基本操作
 * 2. 支持分页查询
 * 3. 支持根据名称模糊查询
 * 4. 支持按 POI 类型查询
 */
@Repository
public interface POIRepository extends JpaRepository<POI, Long> {

    /**
     * 根据名称模糊查询（忽略大小写）
     */
    List<POI> findByNameContainingIgnoreCase(String name);

    /**
     * 根据 POI 类型查询
     */
    List<POI> findByPoiType(POI.POIType poiType);

    /**
     * 分页查询
     */
    Page<POI> findAll(Pageable pageable);

    /**
     * 检查是否存在相同名称
     */
    boolean existsByName(String name);
}
