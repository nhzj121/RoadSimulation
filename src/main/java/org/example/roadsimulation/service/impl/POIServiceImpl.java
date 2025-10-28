package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.dto.POIDTO;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.repository.POIRepository;
import org.example.roadsimulation.service.POIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * POI Service 实现类
 *
 * 功能说明：
 * 1. 实现 POIService 接口中的所有方法
 * 2. 提供 POI 的 CRUD（增删改查）操作
 * 3. 提供分页查询、按名称模糊查询、按类型查询等功能
 * 4. 使用 Spring 事务管理，保证数据一致性
 */
@Service
@Transactional // 默认所有方法都是可写事务
public class POIServiceImpl implements POIService {

    private final POIRepository poiRepository;

    @Autowired
    public POIServiceImpl(POIRepository poiRepository) {
        this.poiRepository = poiRepository;
    }

    /**
     * 创建新的 POI
     *
     * @param poi 待创建的 POI 对象
     * @return 返回创建后的 POI（包含自动生成的 ID）
     * @throws IllegalArgumentException 如果 POI 名称已存在，则抛出异常
     */
    @Override
    public POI create(POI poi) {
        if(poiRepository.existsByName(poi.getName())) {
            throw new IllegalArgumentException("POI 名称已存在: " + poi.getName());
        }
        return poiRepository.save(poi);
    }

    /**
     * 更新指定 ID 的 POI 信息
     *
     * @param id 待更新 POI 的 ID
     * @param poiDetails 包含更新内容的 POI 对象
     * @return 返回更新后的 POI 对象
     * @throws RuntimeException 如果 POI 不存在
     * @throws IllegalArgumentException 如果更新后的 POI 名称与其他 POI 冲突
     */
    @Override
    public POI update(Long id, POI poiDetails) {
        return poiRepository.findById(id)
                .map(poi -> {
                    // 检查新名称是否冲突
                    if(!poi.getName().equals(poiDetails.getName()) &&
                            poiRepository.existsByName(poiDetails.getName())) {
                        throw new IllegalArgumentException("POI 名称已存在: " + poiDetails.getName());
                    }
                    poi.setName(poiDetails.getName());
                    poi.setLongitude(poiDetails.getLongitude());
                    poi.setLatitude(poiDetails.getLatitude());
                    poi.setPoiType(poiDetails.getPoiType());
                    return poiRepository.save(poi);
                })
                .orElseThrow(() -> new RuntimeException("POI 不存在，ID: " + id));
    }

    /**
     * 删除指定 ID 的 POI
     *
     * @param id 待删除 POI 的 ID
     * @throws RuntimeException 如果 POI 不存在
     * @throws IllegalStateException 如果 POI 仍有关联车辆，禁止删除
     */
    @Override
    public void delete(Long id) {
        POI poi = poiRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("POI 不存在，ID: " + id));
        // 检查是否有车辆关联
        if(!poi.getVehiclesAtLocation().isEmpty()) {
            throw new IllegalStateException("无法删除 POI，存在关联车辆");
        }
        poiRepository.delete(poi);
    }

    /**
     * 根据 ID 查询 POI
     *
     * @param id POI ID
     * @return Optional 包含 POI 对象，如果不存在则为空
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<POI> getById(Long id) {
        return poiRepository.findById(id);
    }

    /**
     * 查询所有 POI
     *
     * @return 返回 POI 列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<POI> getAll() {
        return poiRepository.findAll();
    }

    /**
     * 分页查询所有 POI
     *
     * @param pageable 分页参数（页码、每页条数、排序）
     * @return 返回 Page 对象，包含分页信息和当前页 POI
     */
    @Override
    @Transactional(readOnly = true)
    public Page<POI> getAll(Pageable pageable) {
        return poiRepository.findAll(pageable);
    }

    /**
     * 根据名称模糊查询 POI（忽略大小写）
     *
     * @param name POI 名称关键字
     * @return 返回匹配的 POI 列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<POI> searchByName(String name) {
        return poiRepository.findByNameContainingIgnoreCase(name);
    }

    /**
     * 根据 POI 类型查询
     *
     * @param poiType POI 类型（枚举）
     * @return 返回指定类型的 POI 列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<POI> findByType(POI.POIType poiType) {
        return poiRepository.findByPoiType(poiType);
    }

    /**
     * 判断 POI 名称是否存在
     *
     * @param name 待检查名称
     * @return true 表示存在，false 表示不存在
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existsByName(String name) {
        return poiRepository.existsByName(name);
    }

    // ================ 新增的方法实现 ================

    /**
     * 根据 ID 判断 POI 是否存在
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existsById(Long id) {
        return poiRepository.existsById(id);
    }

    /**
     * 根据 ID 获取 POI 实体（不包装为 Optional）
     */
    @Override
    @Transactional(readOnly = true)
    public POI getPOIEntityById(Long id) {
        return poiRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("POI 不存在，ID: " + id));
    }

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
     * 转换前端POI类型字符串为枚举类型
     */
    @Override
    public POI.POIType convertPOIType(String frontendType) {
        return switch (frontendType) {
            case "工厂" -> POI.POIType.FACTORY;
            case "仓库" -> POI.POIType.WAREHOUSE;
            case "加油站" -> POI.POIType.GAS_STATION;
            case "维修中心" -> POI.POIType.MAINTENANCE_CENTER;
            case "休息区" -> POI.POIType.REST_AREA;
            case "运输中心" -> POI.POIType.DISTRIBUTION_CENTER;
            default -> throw new IllegalArgumentException("未知的POI类型: " + frontendType);
        };
    }

    /**
     * 批量保存POI数据
     */
    @Override
    @Transactional
    public List<POI> batchSavePOIs(List<POIDTO> poiDTOs) {
        return poiDTOs.stream()
                .map(this::savePOIFromFrontend)
                .collect(Collectors.toList());
    }
}