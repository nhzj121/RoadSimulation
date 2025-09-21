package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.POI;

import java.util.List;
import java.util.Optional;

public interface POIService {

    // 保存或更新 POI
    POI savePOI(POI poi);

    // 根据 ID 查询
    Optional<POI> findById(Long id);

    // 查询所有 POI
    List<POI> findAll();

    // 根据名称模糊查询
    List<POI> findByNameContaining(String name);

    // 删除 POI
    void deleteById(Long id);
}
