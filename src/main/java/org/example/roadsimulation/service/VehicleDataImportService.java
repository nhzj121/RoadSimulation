package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.Vehicle;
import java.util.List;
import java.util.Map;

public interface VehicleDataImportService {

    /**
     * 导入车辆数据
     * @return 导入的车辆数量
     */
    int importVehicleData();

    /**
     * 从JSON数据转换为Vehicle实体
     * @param vehicleData JSON数据
     * @return Vehicle实体
     */
    Vehicle convertToVehicleEntity(Map<String, Object> vehicleData);

    /**
     * 从车辆名称中提取品牌
     * @param name 车辆名称
     * @return 品牌名称
     */
    String extractBrandFromName(String name);

    /**
     * 从车辆名称中提取车型
     * @param name 车辆名称
     * @return 车型名称
     */
    String extractModelTypeFromName(String name);

    /**
     * 解析尺寸数据
     * @param dimensionStr 尺寸字符串
     * @return 尺寸数值
     */
    Double parseDimension(String dimensionStr);

    /**
     * 获取所有导入的车辆
     * @return 车辆列表
     */
    List<Vehicle> getAllImportedVehicles();

    /**
     * 根据车牌号查找车辆
     * @param licensePlate 车牌号
     * @return 车辆实体
     */
    Vehicle findVehicleByLicensePlate(String licensePlate);
}