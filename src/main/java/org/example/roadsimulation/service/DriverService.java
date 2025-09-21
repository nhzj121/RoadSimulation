package org.example.roadsimulation.service;

import org.example.roadsimulation.entity.Driver;

import java.util.List;
import java.util.Optional;

public interface DriverService {

    Driver saveDriver(Driver driver);

    void deleteDriver(Long id);

    Optional<Driver> findById(Long id);

    List<Driver> findAll();

    List<Driver> findByDriverName(String driverName);

    List<Driver> findByCurrentStatus(Driver.DriverStatus status);

    Driver findByDriverPhone(String driverPhone);
}

