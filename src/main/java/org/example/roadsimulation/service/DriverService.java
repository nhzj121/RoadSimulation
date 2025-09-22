package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.Driver;
import org.example.roadsimulation.entity.Driver.DriverStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * DriverService
 *
 * 定义对 Driver 实体的业务接口（仅定义契约，不实现细节）。
 * 使用接口的好处：
 * - 解耦调用方与实现，便于单元测试和后期替换实现
 * - 团队协作时可以先定义接口，再并行实现
 */
public interface DriverService {

    /**
     * 新增或更新一个司机。
     * - 当 driver.id 为空时为新增
     * - 当 driver.id 不为空且存在时为更新
     *
     * @param driver 要保存或更新的 Driver 对象
     * @return 保存后的 Driver（包含自动生成的 id）
     */
    Driver save(Driver driver);

    /**
     * 根据 ID 查找司机
     *
     * @param id 司机 ID
     * @return Optional 包裹可能存在的 Driver
     */
    Optional<Driver> findById(Long id);

    /**
     * 查询所有司机（无分页）
     *
     * @return 全部司机列表
     */
    List<Driver> findAll();

    /**
     * 分页查询所有司机（用于前端分页）
     *
     * @param pageable Spring Data 的分页参数
     * @return 分页结果
     */
    Page<Driver> findAll(Pageable pageable);

    /**
     * 根据手机号查找司机
     *
     * @param phone 手机号
     * @return 找到的 Driver 或 null（如果不存在）
     */
    Driver findByPhone(String phone);

    /**
     * 根据姓名模糊查询（忽略大小写）
     *
     * @param partialName 部分姓名
     * @return 匹配的司机列表
     */
    List<Driver> searchByName(String partialName);

    /**
     * 根据状态查找司机
     *
     * @param status 枚举状态
     * @return 符合状态的司机列表
     */
    List<Driver> findByStatus(DriverStatus status);

    /**
     * 删除司机（如果存在）
     *
     * @param id 要删除的司机 ID
     */
    void deleteById(Long id);

    /**
     * 检查司机是否存在
     *
     * @param id 司机 ID
     * @return 存在返回 true，否则 false
     */
    boolean existsById(Long id);
}

