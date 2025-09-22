package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.POI;

import java.util.List;
import java.util.Optional;

/**
 * Service 层接口：定义 POI 的业务功能（CRUD）
 */
public interface POIService {

    /**
     * 新增 POI
     */
    POI create(POI poi);

    /**
     * 根据 ID 查询 POI
     */
    Optional<POI> getById(Long id);

    /**
     * 查询所有 POI
     */
    List<POI> getAll();

    /**
     * 更新 POI
     */
    POI update(Long id, POI poi);

    /**
     * 删除 POI
     */
    void delete(Long id);
}
