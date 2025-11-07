package org.example.roadsimulation.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.VehicleRepository;
import org.example.roadsimulation.service.VehicleDataImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.util.*;

@Service
@Transactional
public class VehicleDataImportServiceImpl implements VehicleDataImportService {

    private static final Logger logger = LoggerFactory.getLogger(VehicleDataImportServiceImpl.class);

    private final VehicleRepository vehicleRepository;
    private final ObjectMapper objectMapper;

    @Value("classpath:car_data.json")
    private Resource carDataResource;

    // 存储导入的车辆数据
    private final List<Vehicle> importedVehicles = new ArrayList<>();

    public VehicleDataImportServiceImpl(VehicleRepository vehicleRepository, ObjectMapper objectMapper) {
        this.vehicleRepository = vehicleRepository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    @Override
    public int importVehicleData() {
        try {
            logger.info("开始导入车辆数据...");

            // 读取JSON文件
            Map<String, Map<String, Object>> carData = objectMapper.readValue(
                    carDataResource.getInputStream(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Map<String, Object>>>() {}
            );

            int importedCount = 0;
            importedVehicles.clear();

            // 转换为Vehicle实体并保存
            for (Map.Entry<String, Map<String, Object>> entry : carData.entrySet()) {
                Map<String, Object> vehicleData = entry.getValue();

                try {
                    Vehicle vehicle = convertToVehicleEntity(vehicleData);

                    // 检查是否已存在，避免重复导入
                    if (!vehicleRepository.existsByLicensePlate(vehicle.getLicensePlate())) {
                        Vehicle savedVehicle = vehicleRepository.save(vehicle);
                        importedVehicles.add(savedVehicle);
                        importedCount++;
                        logger.debug("成功导入车辆: {}", savedVehicle.getLicensePlate());
                    } else {
                        logger.debug("车辆已存在，跳过导入: {}", vehicle.getLicensePlate());
                    }

                } catch (Exception e) {
                    logger.error("导入车辆数据失败: {}, 错误: {}", vehicleData.get("id"), e.getMessage());
                }
            }

            logger.info("车辆数据导入完成，共导入 {} 种车型", importedCount);
            return importedCount;

        } catch (Exception e) {
            logger.error("车辆数据导入失败: {}", e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public Vehicle convertToVehicleEntity(Map<String, Object> vehicleData) {
        Vehicle vehicle = new Vehicle();

        // 使用车型ID作为车牌号（需要确保唯一性）
        String vehicleId = (String) vehicleData.get("id");
        vehicle.setLicensePlate(vehicleId);

        // 从name字段中提取品牌和车型
        String name = (String) vehicleData.get("name");
        vehicle.setBrand(extractBrandFromName(name));
        vehicle.setModelType(extractModelTypeFromName(name));

        // 设置车辆类型
        vehicle.setVehicleType((String) vehicleData.get("vehicle_type"));

        // 解析载重能力（去除"吨"单位）
        String currentLoadStr = (String) vehicleData.get("current_load");
        if (currentLoadStr != null && !currentLoadStr.equals("none")) {
            double load = parseLoadCapacity(currentLoadStr);
            vehicle.setMaxLoadCapacity(load);
            vehicle.setCurrentLoad(0.0); // 初始当前载重为0
        } else {
            vehicle.setMaxLoadCapacity(0.0);
            vehicle.setCurrentLoad(0.0);
        }

        // 解析尺寸数据
        vehicle.setLength(parseDimension((String) vehicleData.get("length")));
        vehicle.setWidth(parseDimension((String) vehicleData.get("width")));
        vehicle.setHeight(parseDimension((String) vehicleData.get("height")));

        // 解析速度数据
        String speedStr = (String) vehicleData.get("speed");
        if (speedStr != null && !speedStr.equals("none")) {
            // 如果需要存储速度，可以在Vehicle实体中添加speed字段
            // vehicle.setSpeed(parseSpeed(speedStr));
        }

        // 设置默认状态
        vehicle.setCurrentStatus(Vehicle.VehicleStatus.IDLE);

        return vehicle;
    }

    @Override
    public String extractBrandFromName(String name) {
        if (name == null) {
            return "未知品牌";
        }

        // 简单提取品牌逻辑，可根据实际数据调整
        if (name.contains("中国重汽") || name.contains("HOWO")) return "中国重汽";
        if (name.contains("东风")) return "东风";
        if (name.contains("菱势")) return "菱势";
        if (name.contains("解放")) return "解放";
        if (name.contains("福田")) return "福田";
        if (name.contains("江淮")) return "江淮";

        return "其他品牌";
    }

    @Override
    public String extractModelTypeFromName(String name) {
        if (name == null) {
            return "未知车型";
        }

        // 提取具体车型名称 - 取第一个空格前的部分作为车型
        String[] parts = name.split(" ");
        if (parts.length > 0) {
            return parts[0];
        }
        return name;
    }

    @Override
    public Double parseDimension(String dimensionStr) {
        if (dimensionStr == null || dimensionStr.equals("none") || dimensionStr.trim().isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(dimensionStr.replace("米", "").trim());
        } catch (NumberFormatException e) {
            logger.warn("尺寸数据解析失败: {}", dimensionStr);
            return null;
        }
    }

    /**
     * 解析载重能力
     * @param loadStr 载重字符串
     * @return 载重数值
     */
    private Double parseLoadCapacity(String loadStr) {
        if (loadStr == null || loadStr.equals("none") || loadStr.trim().isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(loadStr.replace("吨", "").trim());
        } catch (NumberFormatException e) {
            logger.warn("载重数据解析失败: {}", loadStr);
            return 0.0;
        }
    }

    /**
     * 解析速度数据
     * @param speedStr 速度字符串
     * @return 速度数值
     */
    private Double parseSpeed(String speedStr) {
        if (speedStr == null || speedStr.equals("none") || speedStr.trim().isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(speedStr.replace("km/h", "").trim());
        } catch (NumberFormatException e) {
            logger.warn("速度数据解析失败: {}", speedStr);
            return null;
        }
    }

    @Override
    public List<Vehicle> getAllImportedVehicles() {
        return new ArrayList<>(importedVehicles);
    }

    @Override
    public Vehicle findVehicleByLicensePlate(String licensePlate) {
        return importedVehicles.stream()
                .filter(vehicle -> licensePlate.equals(vehicle.getLicensePlate()))
                .findFirst()
                .orElse(null);
    }
}