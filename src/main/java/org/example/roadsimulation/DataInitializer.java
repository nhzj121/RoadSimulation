package org.example.roadsimulation;

import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.entity.Driver;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.repository.VehicleRepository;
import org.example.roadsimulation.repository.DriverRepository;
import org.example.roadsimulation.repository.POIRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private POIRepository poiRepository;

    @Override
    public void run(String... args) throws Exception {
//        POI factory1 = new POI("创新服装加工厂", 104.294693,30.964864 , POI.POIType.FACTORY);
//        POI factory2 = new POI("成都仿真植物造景工厂", 104.166675, 30.721025, POI.POIType.FACTORY);
//        POI factory3 = new POI("断桥铝系统窗·工厂直营店", 103.910267, 30.636943, POI.POIType.FACTORY);
        initDrivers();
        initVehicles();
    }



    private void initDrivers() {
        if (driverRepository.count() == 0) {
            Driver driver1 = new Driver();
            driver1.setDriverName("张三");
            driver1.setDriverPhone("13800138001");
            driver1.setCurrentStatus(Driver.DriverStatus.IDLE);

            Driver driver2 = new Driver();
            driver2.setDriverName("李四");
            driver2.setDriverPhone("13800138002");
            driver2.setCurrentStatus(Driver.DriverStatus.IDLE);

            driverRepository.saveAll(Arrays.asList(driver1, driver2));
            System.out.println("初始化司机完成");
        }
    }

    private void initVehicles() {
        // 检查是否已有车辆数据
        if (vehicleRepository.count() > 0) {
            System.out.println("车辆数据已存在，跳过初始化");
            return;
        }
        // 通过ID查找POI，失败时给出友好提示
        POI factory1 = poiRepository.findById(1L).orElseGet(() -> {
            System.out.println("⚠️  警告：未找到ID为1的POI，车辆将不设置当前位置");
            return null;
        });

        POI factory2 = poiRepository.findById(2L).orElseGet(() -> {
            System.out.println("⚠️  警告：未找到ID为2的POI，车辆将不设置当前位置");
            return null;
        });

        POI factory3 = poiRepository.findById(3L).orElseGet(() -> {
            System.out.println("⚠️  警告：未找到ID为3的POI，车辆将不设置当前位置");
            return null;
        });

        // 使用不同的车牌号
        Vehicle vehicle1 = createVehicleIfNotExists("京A12345", "东风", "DF-100", 10.0, "平板车", factory1);
        Vehicle vehicle2 = createVehicleIfNotExists("京B67890", "解放", "JF-200", 15.0, "高护栏", factory2);
        Vehicle vehicle3 = createVehicleIfNotExists("津C54321", "重汽", "CQ-300", 20.0, "全封闭", factory3);


        System.out.println("初始化车辆完成");
    }

    private Vehicle createVehicleIfNotExists(String licensePlate, String brand, String modelType,
                                             Double maxLoadCapacity, String vehicleType, POI currentPOI) {
        // 检查是否已存在该车牌
        Optional<Vehicle> existing = vehicleRepository.findByLicensePlate(licensePlate);
        if (existing.isPresent()) {
            System.out.println("车辆 " + licensePlate + " 已存在，跳过创建");
            return existing.get();
        }

        Vehicle vehicle = new Vehicle();
        vehicle.setLicensePlate(licensePlate);
        vehicle.setBrand(brand);
        vehicle.setModelType(modelType);
        vehicle.setMaxLoadCapacity(maxLoadCapacity);
        vehicle.setVehicleType(vehicleType);
        vehicle.setCurrentStatus(Vehicle.VehicleStatus.IDLE);
        vehicle.setCurrentPOI(currentPOI);

        return vehicleRepository.save(vehicle);
    }
}