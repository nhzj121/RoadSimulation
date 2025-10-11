// POIService.java
package org.example.roadsimulation.service;

import org.example.roadsimulation.dto.POIDTO;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.repository.POIRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class POIService {

    @Autowired
    private POIRepository poiRepository;

    /**
     * 将前端POI数据转换为实体并保存
     */
    @Transactional
    public POI savePOIFromFrontend(POIDTO poiDTO) {
        // 检查是否已存在相同的POI
        if (poiRepository.existsByNameAndLocation(poiDTO.getName(),
                poiDTO.getLongitude(), poiDTO.getLatitude()) > 0) {
            throw new RuntimeException("POI已存在: " + poiDTO.getName());
        }

        // 转换POI类型
        POI.POIType poiType = convertPOIType(poiDTO.getType());

        // 创建POI实体
        POI poi = new POI(
                poiDTO.getName(),
                poiDTO.getLongitude(),
                poiDTO.getLatitude(),
                poiType
        );

        return poiRepository.save(poi);
    }

    /**
     * 批量保存POI数据
     */
    @Transactional
    public List<POI> batchSavePOIs(List<POIDTO> poiDTOs) {
        return poiDTOs.stream()
                .map(this::savePOIFromFrontend)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有POI
     */
    public List<POI> getAllPOIs() {
        return poiRepository.findAll();
    }

    /**
     * 根据类型获取POI
     */
    public List<POI> getPOIsByType(POI.POIType poiType) {
        return poiRepository.findByPoiType(poiType);
    }

    /**
     * 根据ID获取POI
     */
    public POI getPOIById(Long id) {
        return poiRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("POI未找到，ID: " + id));
    }

    /**
     * 删除POI
     */
    @Transactional
    public void deletePOI(Long id) {
        if (!poiRepository.existsById(id)) {
            throw new RuntimeException("POI未找到，ID: " + id);
        }
        poiRepository.deleteById(id);
    }

    /**
     * 转换前端POI类型字符串为枚举类型
     */
    private POI.POIType convertPOIType(String frontendType) {
        switch (frontendType) {
            case "工厂":
                return POI.POIType.FACTORY;
            case "仓库":
                return POI.POIType.WAREHOUSE;
            case "加油站":
                return POI.POIType.GAS_STATION;
            case "维修中心":
                return POI.POIType.MAINTENANCE_CENTER;
            case "休息区":
                return POI.POIType.REST_AREA;
            case "运输中心":
                return POI.POIType.DISTRIBUTION_CENTER;
            default:
                throw new IllegalArgumentException("未知的POI类型: " + frontendType);
        }
    }
}