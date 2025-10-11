//package org.example.roadsimulation.service.impl;
//
//import org.example.roadsimulation.entity.POI;
//import org.example.roadsimulation.repository.POIRepository;
//import org.example.roadsimulation.service.POIService;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//
//import java.math.BigDecimal;
//import java.util.List;
//import java.util.Optional;
//
///**
// * Service 实现类：具体实现 CRUD 逻辑
// */
//@Service
//public class POIServiceImpl implements POIService {
//
//    private final POIRepository poiRepository;
//
//    /**
//     * 构造函数注入 Repository，推荐用构造函数而不是字段注入，方便测试
//     */
//    @Autowired
//    public POIServiceImpl(POIRepository poiRepository) {
//        this.poiRepository = poiRepository;
//    }
//
//    /**
//     * 新增 POI
//     */
//    @Override
//    public POI create(POI poi) {
//        return poiRepository.save(poi);
//    }
//
//    /**
//     * 根据 ID 查询 POI
//     */
//    @Override
//    public Optional<POI> getById(Long id) {
//        return poiRepository.findById(id);
//    }
//
//    /**
//     * 查询所有 POI
//     */
//    @Override
//    public List<POI> getAll() {
//        return poiRepository.findAll();
//    }
//
//    /**
//     * 更新 POI
//     * 1. 查找是否存在
//     * 2. 存在则更新
//     * 3. 不存在抛异常
//     */
//    @Override
//    public POI update(Long id, POI poi) {
//        return poiRepository.findById(id).map(existing -> {
//            existing.setName(poi.getName());
//            existing.setLongitude(poi.getLongitude());
//            existing.setLatitude(poi.getLatitude());
//            existing.setType(poi.getType());
//            return poiRepository.save(existing);
//        }).orElseThrow(() -> new RuntimeException("POI with id " + id + " not found"));
//    }
//
//    /**
//     * 删除 POI
//     */
//    @Override
//    public void delete(Long id) {
//        if (!poiRepository.existsById(id)) {
//            throw new RuntimeException("POI with id " + id + " not found");
//        }
//        poiRepository.deleteById(id);
//    }
//
//    @Override
//    public boolean existsById(Long poiId){
//        return poiRepository.existsById(poiId);
//    }
//
//    // 新增的业务方法实现
//
//    @Override
//    public Optional<POI> getByName(String name) {
//        return poiRepository.findByName(name);
//    }
//
//    @Override
//    public List<POI> searchByName(String name) {
//        return poiRepository.findByNameContainingIgnoreCase(name);
//    }
//
//    @Override
//    public List<POI> getByType(POI.POIType poiType) {
//        return poiRepository.findByPoiType(poiType);
//    }
//
//    @Override
//    public List<POI> getByLocationRange(BigDecimal minLon, BigDecimal maxLon,
//                                        BigDecimal minLat, BigDecimal maxLat) {
//        return poiRepository.findByLocationWithin(minLon, maxLon, minLat, maxLat);
//    }
//
//    @Override
//    public List<POI> getNearbyPOIs(BigDecimal centerLon, BigDecimal centerLat, BigDecimal radius) {
//        return poiRepository.findNearbyPOIs(centerLon, centerLat, radius);
//    }
//
//    @Override
//    public boolean existsByName(String name) {
//        return poiRepository.existsByName(name);
//    }
//
//    @Override
//    public List<POI> getByNameAndType(String name, POI.POIType poiType) {
//        return poiRepository.findByNameAndPoiType(name, poiType);
//    }
//
//    @Override
//    public long countByType(POI.POIType poiType) {
//        return poiRepository.countByPoiType(poiType);
//    }
//}