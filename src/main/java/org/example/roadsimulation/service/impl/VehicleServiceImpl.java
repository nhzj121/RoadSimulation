package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.entity.Vehicle;
import org.example.roadsimulation.repository.VehicleRepository;
import org.example.roadsimulation.service.VehicleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class VehicleServiceImpl implements VehicleService {

    private final VehicleRepository vehicleRepository;

    @Autowired
    public VehicleServiceImpl(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    @Override
    public Vehicle saveVehicle(Vehicle vehicle) {
        return vehicleRepository.save(vehicle);
    }

    @Override
    public void deleteVehicle(Long id) {
        vehicleRepository.deleteById(id);
    }

    @Override
    public Optional<Vehicle> findById(Long id) {
        return vehicleRepository.findById(id);
    }

    @Override
    public List<Vehicle> findAll() {
        return vehicleRepository.findAll();
    }

    @Override
    public Vehicle findByLicensePlate(String licensePlate) {
        return vehicleRepository.findByLicensePlate(licensePlate);
    }

    @Override
    public List<Vehicle> findByCurrentStatus(Vehicle.VehicleStatus status) {
        return vehicleRepository.findByCurrentStatus(status);
    }

    @Override
    public List<Vehicle> findByVehicleType(String vehicleType) {
        return vehicleRepository.findByVehicleType(vehicleType);
    }

    @Override
    public List<Vehicle> findByCurrentPOIId(Long poiId) {
        return vehicleRepository.findByCurrentPOIId(poiId);
    }

    @Override
    public List<Vehicle> findSuitableIdleVehicles(Double requiredCapacity) {
        return vehicleRepository.findSuitableIdleVehicles(requiredCapacity);
    }
}
