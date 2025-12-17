package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.Vehicle;

import java.util.List;
import java.util.Map;

/**
 * 车辆初始化服务接口
 * 负责将车辆重置到标准化的初始状态
 */
public interface VehicleInitializationService {

    /**
     * 初始化所有车辆状态
     * 将所有车辆设置为空闲状态，并放置到默认POI
     */
    void initializeAllVehicleStatus();

    /**
     * 重置单个车辆到指定POI
     * @param vehicleId 车辆ID
     * @param poiId 目标POI ID
     * @return 重置后的车辆
     */
    Vehicle resetVehicleToPOI(Long vehicleId, Long poiId);

    /**
     * 批量重置车辆位置
     * @param vehicleIds 车辆ID列表
     * @param poiId 目标POI ID
     * @return 重置成功的数量
     */
    int batchResetVehiclesToPOI(List<Long> vehicleIds, Long poiId);

    /**
     * 清空车辆载重
     * @param vehicleId 车辆ID
     * @return 清空后的车辆
     */
    Vehicle clearVehicleLoad(Long vehicleId);

    /**
     * 批量清空车辆载重
     * @param vehicleIds 车辆ID列表
     * @return 清空成功的数量
     */
    int batchClearVehicleLoads(List<Long> vehicleIds);

    /**
     * 设置车辆为空闲状态
     * @param vehicleId 车辆ID
     * @return 设置后的车辆
     */
    Vehicle setVehicleToIdle(Long vehicleId);

    /**
     * 批量设置车辆为空闲状态
     * @param vehicleIds 车辆ID列表
     * @return 设置成功的数量
     */
    int batchSetVehiclesToIdle(List<Long> vehicleIds);

    /**
     * 获取车辆初始化统计信息
     * @return 统计信息
     */
    Map<String, Object> getInitializationStats();

    /**
     * 获取可用的默认POI（用于初始化）
     * @return POI ID列表
     */
    List<Long> getAvailableDefaultPOIs();

    /**
     * 设置默认POI（用于初始化）
     * @param poiId POI ID
     */
    void setDefaultPOI(Long poiId);

    /**
     * 验证车辆是否可以初始化
     * @param vehicleId 车辆ID
     * @return 是否可以初始化
     */
    boolean canInitializeVehicle(Long vehicleId);

    /**
     * 获取车辆当前状态信息
     * @param vehicleId 车辆ID
     * @return 状态信息
     */
    Map<String, Object> getVehicleStatusInfo(Long vehicleId);
}