package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.Driver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * DriverRepository
 *
 * 说明：
 * - 继承 JpaRepository 可以直接使用基本的 CRUD 方法（save, findById, findAll, deleteById 等）。
 * - 在此定义的按方法名自动解析的查询方法将由 Spring Data JPA 自动实现。
 */
@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {

    /**
     * 根据司机姓名精确查找（全名匹配）
     */
    List<Driver> findByDriverName(String driverName);

    /**
     * 根据司机姓名模糊查找（忽略大小写）
     * 例如："张" 可以匹配 "张三"、"张小明"
     */
    List<Driver> findByDriverNameContainingIgnoreCase(String partialName);

    /**
     * 根据手机号查找（假定手机号唯一）
     */
    Driver findByDriverPhone(String driverPhone);

    /**
     * 根据司机状态查找（例如：IDLE, ASSIGNED, OFF）
     */
    List<Driver> findByCurrentStatus(Driver.DriverStatus status);

    /**
     * 分页查询所有司机（方便前端分页显示）
     */
    Page<Driver> findAll(Pageable pageable);
}

