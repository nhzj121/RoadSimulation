package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {

    // 根据司机姓名查询
    List<Driver> findByDriverName(String driverName);

    // 根据司机状态查询
    List<Driver> findByCurrentStatus(Driver.DriverStatus status);

    // 根据手机号查询
    Driver findByDriverPhone(String driverPhone);
}

