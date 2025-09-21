package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.entity.Driver;
import org.example.roadsimulation.repository.DriverRepository;
import org.example.roadsimulation.service.DriverService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DriverServiceImpl implements DriverService {

    private final DriverRepository driverRepository;

    @Autowired
    public DriverServiceImpl(DriverRepository driverRepository) {
        this.driverRepository = driverRepository;
    }

    @Override
    public Driver saveDriver(Driver driver) {
        return driverRepository.save(driver);
    }

    @Override
    public void deleteDriver(Long id) {
        driverRepository.deleteById(id);
    }

    @Override
    public Optional<Driver> findById(Long id) {
        return driverRepository.findById(id);
    }

    @Override
    public List<Driver> findAll() {
        return driverRepository.findAll();
    }

    @Override
    public List<Driver> findByDriverName(String driverName) {
        return driverRepository.findByDriverName(driverName);
    }

    @Override
    public List<Driver> findByCurrentStatus(Driver.DriverStatus status) {
        return driverRepository.findByCurrentStatus(status);
    }

    @Override
    public Driver findByDriverPhone(String driverPhone) {
        return driverRepository.findByDriverPhone(driverPhone);
    }
}
