package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.POI;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Repository层：POI 实体的数据库操作接口
 * 继承 JpaRepository<POI, Long> 就可以获得 CRUD 基本方法：
 *  - save() 新增或更新
 *  - findById() 按ID查找
 *  - findAll() 查询全部
 *  - deleteById() 按ID删除
 *  - existsById() 判断是否存在
 */
@Repository
public interface POIRepository extends JpaRepository<POI, Long> {

    /**
     * 根据名称查询POI（精确匹配）
     */
    Optional<POI> findByName(String name);

    /**
     * 根据名称模糊查询POI
     */
    List<POI> findByNameContainingIgnoreCase(String name);

    /**
     * 根据POI类型查询
     */
    List<POI> findByPoiType(POI.POIType poiType);

    /**
     * 根据经纬度范围查询POI
     * @param minLon 最小经度
     * @param maxLon 最大经度
     * @param minLat 最小纬度
     * @param maxLat 最大纬度
     */
    @Query("SELECT p FROM POI p WHERE p.longitude BETWEEN :minLon AND :maxLon AND p.latitude BETWEEN :minLat AND :maxLat")
    List<POI> findByLocationWithin(@Param("minLon") BigDecimal minLon,
                                   @Param("maxLon") BigDecimal maxLon,
                                   @Param("minLat") BigDecimal minLat,
                                   @Param("maxLat") BigDecimal maxLat);

    /**
     * 根据指定位置和半径（近似）查询附近的POI
     * 使用简化的矩形区域近似圆形区域查询
     */
    @Query("SELECT p FROM POI p WHERE " +
            "p.longitude BETWEEN :centerLon - :radius AND :centerLon + :radius AND " +
            "p.latitude BETWEEN :centerLat - :radius AND :centerLat + :radius")
    List<POI> findNearbyPOIs(@Param("centerLon") BigDecimal centerLon,
                             @Param("centerLat") BigDecimal centerLat,
                             @Param("radius") BigDecimal radius);

    /**
     * 检查指定名称的POI是否存在
     */
    boolean existsByName(String name);

    /**
     * 根据名称和类型查询POI
     */
    List<POI> findByNameAndPoiType(String name, POI.POIType poiType);

    /**
     * 统计指定类型的POI数量
     */
    long countByPoiType(POI.POIType poiType);

    /**
     * 根据类型删除POI
     */
    void deleteByPoiType(POI.POIType poiType);

    // 根据经纬度范围查找POI
    @Query("SELECT p FROM POI p WHERE p.longitude BETWEEN :minLng AND :maxLng AND p.latitude BETWEEN :minLat AND :maxLat")
    List<POI> findByLocationRange(BigDecimal minLng, BigDecimal maxLng, BigDecimal minLat, BigDecimal maxLat);

    // 检查是否已存在相同名称和位置的POI
    @Query("SELECT COUNT(p) FROM POI p WHERE p.name = :name AND p.longitude = :longitude AND p.latitude = :latitude")
    int existsByNameAndLocation(String name, BigDecimal longitude, BigDecimal latitude);
}