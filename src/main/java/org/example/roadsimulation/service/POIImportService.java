package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.repository.POIRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * POI 数据导入服务
 * 用于批量导入 POI 数据到数据库
 */
@Service
public class POIImportService {

    private final POIRepository poiRepository;

    @Autowired
    public POIImportService(POIRepository poiRepository) {
        this.poiRepository = poiRepository;
    }

    /**
     * 批量导入 POI 数据
     * @param poiDataList POI 数据列表
     * @return 成功导入的数量
     */
    @Transactional
    public int batchImportPOIs(List<POIData> poiDataList) {
        List<POI> poisToSave = new ArrayList<>();
        int savedCount = 0;

        for (POIData data : poiDataList) {
            // 检查是否已存在相同名称和位置的 POI
            if (poiRepository.existsByNameAndLocation(data.name, data.longitude, data.latitude) > 0) {
                System.out.println("跳过已存在的 POI: " + data.name);
                continue;
            }

            POI poi = new POI();
            poi.setName(data.name);
            poi.setLongitude(data.longitude);
            poi.setLatitude(data.latitude);
            poi.setPoiType(data.poiType);

            poisToSave.add(poi);

            // 每 100 条批量保存一次
            if (poisToSave.size() >= 100) {
                poiRepository.saveAll(poisToSave);
                savedCount += poisToSave.size();
                poisToSave.clear();
                System.out.println("已导入 " + savedCount + " 条 POI 数据");
            }
        }

        // 保存剩余数据
        if (!poisToSave.isEmpty()) {
            poiRepository.saveAll(poisToSave);
            savedCount += poisToSave.size();
        }

        return savedCount;
    }

    /**
     * POI 数据内部类
     */
    public static class POIData {
        public Long id;
        public String name;
        public BigDecimal longitude;
        public BigDecimal latitude;
        public POI.POIType poiType;

        public POIData(String name, BigDecimal longitude, BigDecimal latitude, POI.POIType poiType) {
            this.name = name;
            this.longitude = longitude;
            this.latitude = latitude;
            this.poiType = poiType;
        }
    }
}
