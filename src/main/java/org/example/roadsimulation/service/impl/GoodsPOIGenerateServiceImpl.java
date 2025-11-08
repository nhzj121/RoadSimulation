package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.repository.GoodsRepository;
import org.example.roadsimulation.repository.POIRepository;
import org.example.roadsimulation.service.GoodsPOIGenerateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Service
@Transactional
public class GoodsPOIGenerateServiceImpl implements GoodsPOIGenerateService{
    private final POIRepository poiRepository;
    private final GoodsRepository goodsRepository;

    private static final Logger logger = LoggerFactory.getLogger(GoodsPOIGenerateServiceImpl.class);

    @Autowired
    public GoodsPOIGenerateServiceImpl(POIRepository poiRepository, GoodsRepository goodsRepository) {
        this.poiRepository = poiRepository;
        this.goodsRepository = goodsRepository;
    }

    /**
     *  通过 姓名关键字 和 种类 进行筛选
     */
    @Override
    public List<POI> getGoalPOIList(String keyWord, POI.POIType poiType){
        logger.debug("查询POI - 关键字: {}, 类型: {}", keyWord, poiType);
        System.out.println("调用getGoalPOIList方法，其中keyword为" + keyWord + ", poiType为" +  poiType);
        return poiRepository.findByNameAndPoiType(keyWord, poiType);
    }



}
