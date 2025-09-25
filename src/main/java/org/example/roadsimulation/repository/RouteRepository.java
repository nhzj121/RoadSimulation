package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.Route;
import org.example.roadsimulation.entity.Route.RouteStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RouteRepository extends JpaRepository<Route, Long> {

    // 根据路线编号查询
    Optional<Route> findByRouteCode(String routeCode);

    // 根据状态查询
    List<Route> findByStatus(RouteStatus status);
    Page<Route> findByStatus(RouteStatus status, Pageable pageable);

    // 根据路线类型查询
    List<Route> findByRouteType(String routeType);

    // 根据起点POI查询
    List<Route> findByStartPOIId(Long startPoiId);

    // 根据终点POI查询
    List<Route> findByEndPOIId(Long endPoiId);

    // 根据起点和终点POI查询
    List<Route> findByStartPOIIdAndEndPOIId(Long startPoiId, Long endPoiId);

    // 根据距离范围查询
    List<Route> findByDistanceBetween(Double minDistance, Double maxDistance);

    // 根据预计时间范围查询
    List<Route> findByEstimatedTimeBetween(Double minTime, Double maxTime);

    // 搜索路线名称或编号
    @Query("SELECT r FROM Route r WHERE r.name LIKE %:keyword% OR r.routeCode LIKE %:keyword%")
    List<Route> searchByNameOrCode(@Param("keyword") String keyword);

    // 统计各种状态的路线数量
    @Query("SELECT r.status, COUNT(r) FROM Route r GROUP BY r.status")
    List<Object[]> countRoutesByStatus();

    // 查询最短路径（按距离）
    @Query("SELECT r FROM Route r ORDER BY r.distance ASC")
    List<Route> findShortestRoutes(Pageable pageable);

    // 查询最快路径（按时间）
    @Query("SELECT r FROM Route r ORDER BY r.estimatedTime ASC")
    List<Route> findFastestRoutes(Pageable pageable);

    // 检查路线编号是否存在（用于更新时验证）
    @Query("SELECT COUNT(r) > 0 FROM Route r WHERE r.routeCode = :routeCode AND r.id != :id")
    boolean existsByRouteCodeAndIdNot(@Param("routeCode") String routeCode, @Param("id") Long id);
}