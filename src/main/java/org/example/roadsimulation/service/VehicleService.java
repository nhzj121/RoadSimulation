package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.Vehicle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * VehicleService
 *
 * 定义车队/车辆相关的业务接口（契约）。
 * 所有对外使用的业务方法应在此接口中定义，Controller/其它 Service 应仅依赖此接口。
 */
public interface VehicleService {

    /**
     * 新增或更新车辆（当 vehicle.id 为 null 时新增，否则更新）。
     *
     * @param vehicle 要保存的车辆实体
     * @return 保存后的车辆（包含 id）
     */
    Vehicle save(Vehicle vehicle);

    /**
     * 根据 id 查找车辆
     *
     * @param id 车辆主键
     * @return Optional 包裹可能存在的 Vehicle
     */
    Optional<Vehicle> findById(Long id);

    /**
     * 查找所有车辆（不分页）
     *
     * @return 全部车辆列表
     */
    List<Vehicle> findAll();

    /**
     * 分页查找车辆（用于前端分页）
     *
     * @param pageable 分页参数（页码/大小/排序）
     * @return 分页结果
     */
    Page<Vehicle> findAll(Pageable pageable);

    /**
     * 根据车牌精确查找车辆
     */
    Vehicle findByLicensePlate(String licensePlate);

    /**
     * 车牌模糊搜索
     */
    List<Vehicle> searchByLicensePlate(String partialPlate);

    /**
     * 根据车辆状态查找（例如 IDLE）
     */
    List<Vehicle> findByStatus(Vehicle.VehicleStatus status);

    /**
     * 根据车辆类型查找
     */
    List<Vehicle> findByVehicleType(String vehicleType);

    /**
     * 查找位于某 POI 的车辆
     */
    List<Vehicle> findByCurrentPOIId(Long poiId);

    /**
     * 查找空闲且能满足最低载重要求的车辆（按最小足够载重排序）
     */
    List<Vehicle> findSuitableIdleVehicles(Double requiredCapacity);

    /**
     * 删除车辆（如果存在则删除）
     */
    void deleteById(Long id);

    /**
     * 检查车辆是否存在
     */
    boolean existsById(Long id);
}
