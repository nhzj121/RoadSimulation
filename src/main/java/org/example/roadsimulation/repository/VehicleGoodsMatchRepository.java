package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.VehicleGoodsMatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 车辆 - 货物匹配记录 Repository
 */
@Repository
public interface VehicleGoodsMatchRepository extends JpaRepository<VehicleGoodsMatch, Long> {

    /**
     * 根据货物 ID 查询匹配记录
     */
    List<VehicleGoodsMatch> findByGoodsId(Long goodsId);

    /**
     * 根据车辆 ID 查询匹配记录
     */
    List<VehicleGoodsMatch> findByVehicleId(Long vehicleId);

    /**
     * 根据货物 ID 和匹配状态查询
     */
    List<VehicleGoodsMatch> findByGoodsIdAndMatchStatus(Long goodsId, VehicleGoodsMatch.MatchStatus status);

    /**
     * 根据车辆 ID 和匹配状态查询
     */
    List<VehicleGoodsMatch> findByVehicleIdAndMatchStatus(Long vehicleId, VehicleGoodsMatch.MatchStatus status);

    /**
     * 分页查询匹配记录
     */
    Page<VehicleGoodsMatch> findAll(Pageable pageable);

    /**
     * 根据匹配时间范围查询
     */
    @Query("SELECT m FROM VehicleGoodsMatch m WHERE m.matchTime BETWEEN :startTime AND :endTime")
    List<VehicleGoodsMatch> findByMatchTimeBetween(@Param("startTime") LocalDateTime startTime,
                                                    @Param("endTime") LocalDateTime endTime);

    /**
     * 根据货物名称模糊查询
     */
    @Query("SELECT m FROM VehicleGoodsMatch m WHERE m.goodsName LIKE %:goodsName%")
    List<VehicleGoodsMatch> findByGoodsNameContaining(@Param("goodsName") String goodsName);

    /**
     * 根据车牌号查询
     */
    List<VehicleGoodsMatch> findByLicensePlate(String licensePlate);

    /**
     * 查询指定状态范围内的匹配记录
     */
    List<VehicleGoodsMatch> findByMatchStatusIn(List<VehicleGoodsMatch.MatchStatus> statuses);

    /**
     * 根据货物 SKU 查询匹配记录
     */
    List<VehicleGoodsMatch> findByGoodsSku(String goodsSku);

    /**
     * 查询完全匹配的匹配记录
     */
    List<VehicleGoodsMatch> findByIsFullyMatchedTrue();

    /**
     * 根据出发地 POI ID 查询
     */
    List<VehicleGoodsMatch> findByOriginPoiId(Long originPoiId);

    /**
     * 根据目的地 POI ID 查询
     */
    List<VehicleGoodsMatch> findByDestinationPoiId(Long destinationPoiId);

    /**
     * 统计指定时间范围内的匹配数量
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 匹配数量
     */
    @Query("SELECT COUNT(m) FROM VehicleGoodsMatch m WHERE m.matchTime BETWEEN :startTime AND :endTime")
    Long countByMatchTimeBetween(@Param("startTime") LocalDateTime startTime,
                                  @Param("endTime") LocalDateTime endTime);

    /**
     * 统计指定状态范围内的匹配数量
     */
    Long countByMatchStatusIn(List<VehicleGoodsMatch.MatchStatus> statuses);

    /**
     * 根据货物 ID 和状态删除匹配记录（用于清理）
     */
    void deleteByGoodsIdAndMatchStatusIn(Long goodsId, List<VehicleGoodsMatch.MatchStatus> statuses);
}
