package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.POI;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface POIRepository extends JpaRepository<POI, Long> {

    // 根据POI名称查找
    List<POI> findByNameContainingIgnoreCase(String name);

    // 根据POI类型查找
    List<POI> findByPoiType(POI.POIType type);

    // 根据经纬度范围查找POI
    List<POI> findByLongitudeBetweenAndLatitudeBetween(Double minLon, Double maxLon, Double minLat, Double maxLat);
}

