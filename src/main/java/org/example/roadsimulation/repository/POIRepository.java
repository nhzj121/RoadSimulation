package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.POI;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
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
    @NotNull Page<POI> findAll(@NotNull Pageable pageable);

    /**
     * 检查是否存在相同名称
     */
    boolean existsByName(String name);

    // 根据经纬度范围查找POI
    @Query("SELECT p FROM POI p WHERE p.longitude BETWEEN :minLng AND :maxLng AND p.latitude BETWEEN :minLat AND :maxLat")
    List<POI> findByLocationRange(BigDecimal minLng, BigDecimal maxLng, BigDecimal minLat, BigDecimal maxLat);

    // 检查是否已存在相同名称和位置的POI
    @Query("SELECT COUNT(p) FROM POI p WHERE p.name = :name AND p.longitude = :longitude AND p.latitude = :latitude")
    int existsByNameAndLocation(String name, BigDecimal longitude, BigDecimal latitude);

    // 根据名称和类型进行模糊化查询
    @Query("SELECT p FROM POI p WHERE p.name = :name AND p.poiType = :poiType")
    List<POI> findByNameAndPoiType(String name, POI.POIType poiType);
}
