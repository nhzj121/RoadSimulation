package org.example.roadsimulation.service.impl;

import org.example.roadsimulation.entity.VehicleType;
import org.example.roadsimulation.repository.VehicleTypeRepository;
import org.example.roadsimulation.service.VehicleTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class VehicleTypeServiceImpl implements VehicleTypeService {

    @Autowired
    private VehicleTypeRepository vehicleTypeRepository;

    @Override
    public List<VehicleType> getAllVehicleTypes() {
        return vehicleTypeRepository.findByIsAvailableTrue();
    }

    @Override
    public VehicleType getVehicleTypeById(Long id) {
        return vehicleTypeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("车型不存在: " + id));
    }

    @Override
    public VehicleType createVehicleType(VehicleType vehicleType) {
        if (vehicleTypeRepository.findByCode(vehicleType.getCode()).isPresent()) {
            throw new IllegalArgumentException("车型代码已存在: " + vehicleType.getCode());
        }
        return vehicleTypeRepository.save(vehicleType);
    }

    @Override
    public VehicleType updateVehicleType(Long id, VehicleType vehicleType) {
        VehicleType existing = getVehicleTypeById(id);

        if (!existing.getCode().equals(vehicleType.getCode())) {
            if (vehicleTypeRepository.findByCode(vehicleType.getCode()).isPresent()) {
                throw new IllegalArgumentException("车型代码已存在: " + vehicleType.getCode());
            }
        }

        existing.setName(vehicleType.getName());
        existing.setCode(vehicleType.getCode());
        existing.setCategory(vehicleType.getCategory());
        existing.setMaxLoadWeight(vehicleType.getMaxLoadWeight());
        existing.setMaxLoadVolume(vehicleType.getMaxLoadVolume());
        existing.setMinLoadWeight(vehicleType.getMinLoadWeight());
        existing.setHasTempControl(vehicleType.getHasTempControl());
        existing.setAllowedHazmatLevels(vehicleType.getAllowedHazmatLevels());
        existing.setSpecialFeatures(vehicleType.getSpecialFeatures());
        existing.setFuelConsumption(vehicleType.getFuelConsumption());
        existing.setIsAvailable(vehicleType.getIsAvailable());

        return vehicleTypeRepository.save(existing);
    }

    @Override
    public void deleteVehicleType(Long id) {
        VehicleType vehicleType = getVehicleTypeById(id);
        vehicleType.setIsAvailable(false);
        vehicleTypeRepository.save(vehicleType);
    }

    @Override
    public List<VehicleType> getVehicleTypesByCategory(String category) {
        return vehicleTypeRepository.findByCategoryAndIsAvailableTrue(category);
    }

    @Override
    public List<VehicleType> getVehicleTypesByLoadCapacity(Double minWeight, Double minVolume) {
        return vehicleTypeRepository.findByLoadCapacity(minWeight, minVolume);
    }
}