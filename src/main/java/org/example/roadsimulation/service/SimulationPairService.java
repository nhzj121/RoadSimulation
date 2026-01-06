// SimulationPairService.java
package org.example.roadsimulation.service;

import org.example.roadsimulation.DataInitializer;
import org.example.roadsimulation.dto.POIPairDTO;
import org.example.roadsimulation.entity.*;
import org.example.roadsimulation.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SimulationPairService {

    @Autowired
    private DataInitializer dataInitializer;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private ShipmentItemRepository shipmentItemRepository;

    /**
     * 从活跃配对中获取POI对
     */
    public List<POIPairDTO> getCurrentPOIPairs() {
        return dataInitializer.getCurrentPOIPairs();
    }

    /**
     * 获取新增的POI配对
     */
    public List<POIPairDTO> getNewPOIPairs() {
        return dataInitializer.getNewPOIPairs();
    }

    /**
     * 标记配对为已绘制
     */
    public void markPairAsDrawn(String pairId) {
        dataInitializer.markPairAsDrawn(pairId);
    }

    /**
     * 获取已完成运输的配对ID
     */
    public List<String> getCompletedPairIds() {
        // 这里可以扩展为从数据库查询已完成运输的配对
        // 目前暂时返回空列表，后续可以完善
        return new ArrayList<>();
    }

}