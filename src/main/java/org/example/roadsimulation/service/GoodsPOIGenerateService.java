package org.example.roadsimulation.service;


import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.repository.POIRepository;

import java.util.ArrayList;
import java.util.List;

public interface GoodsPOIGenerateService {

    /**
     * 基本的 POI 获取，List存储，暂时考虑 木材 和 石料
     */
    List<POI> getGoalPOIList(String keyWord, POI.POIType poiType);
}
