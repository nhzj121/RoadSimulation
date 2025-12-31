package org.example.roadsimulation.service;

import org.example.roadsimulation.dto.VehicleMatchResult;
import org.example.roadsimulation.dto.VehicleMatchingCriteria;
import org.example.roadsimulation.entity.Goods;
import org.example.roadsimulation.entity.Vehicle;

import java.util.List;

public interface VehicleMatchingService {

    /**
     * 根据货物信息匹配适合的车辆
     * @param goods 货物信息
     * @param quantity 货物数量
     * @return 匹配结果列表
     */
    List<VehicleMatchResult> matchVehiclesForGoods(Goods goods, Integer quantity);

    /**
     * 根据自定义匹配条件筛选车辆
     * @param criteria 匹配条件
     * @return 匹配结果列表
     */
    List<VehicleMatchResult> matchVehiclesByCriteria(VehicleMatchingCriteria criteria);

    /**
     * 快速匹配 - 仅基于载重需求
     * @param requiredLoad 所需载重
     * @return 匹配的车辆列表
     */
    List<Vehicle> quickMatchByLoadCapacity(Double requiredLoad);

    /**
     * 获取所有可用车辆（空闲状态）
     * @return 可用车辆列表
     */
    List<Vehicle> getAvailableVehicles();

    /**
     * 计算货物总体积
     * @param goods 货物
     * @param quantity 数量
     * @return 总体积
     */
    Double calculateTotalVolume(Goods goods, Integer quantity);

    /**
     * 计算货物总重量
     * @param goods 货物
     * @param quantity 数量
     * @return 总重量
     */
    Double calculateTotalWeight(Goods goods, Integer quantity);

    /**
     * 获取所有品牌列表
     * @return 品牌列表
     */
    List<String> getAllBrands();

    /**
     * 获取所有车辆类型列表
     * @return 车辆类型列表
     */
    List<String> getAllVehicleTypes();

    /**
     * 就近匹配 - 考虑车辆当前位置与出发地距离
     * @param criteria 匹配条件（包含出发地信息）
     * @return 按距离排序的匹配结果
     */
    List<VehicleMatchResult> matchVehiclesByProximity(VehicleMatchingCriteria criteria);

    /**
     * 智能匹配 - 综合考量匹配度和距离
     * @param criteria 匹配条件
     * @param distanceWeight 距离权重（0-1）
     * @return 综合排序的匹配结果
     */
    List<VehicleMatchResult> smartMatchWithDistance(VehicleMatchingCriteria criteria, Double distanceWeight);

    /**
     * 获取推荐车辆（根据货物出发地推荐最近车辆）
     */
    List<Vehicle> getRecommendedVehicles(Long originPoiId, VehicleMatchingCriteria criteria);
}