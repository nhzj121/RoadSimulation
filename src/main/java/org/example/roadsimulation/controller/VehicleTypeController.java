package org.example.roadsimulation.controller;

import org.example.roadsimulation.entity.VehicleType;
import org.example.roadsimulation.service.VehicleTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vehicle-types")
public class VehicleTypeController {

    @Autowired
    private VehicleTypeService vehicleTypeService;

    @GetMapping
    public ResponseEntity<List<VehicleType>> getAllVehicleTypes() {
        List<VehicleType> vehicleTypes = vehicleTypeService.getAllVehicleTypes();
        return ResponseEntity.ok(vehicleTypes);
    }

    @GetMapping("/{id}")
    public ResponseEntity<VehicleType> getVehicleTypeById(@PathVariable Long id) {
        VehicleType vehicleType = vehicleTypeService.getVehicleTypeById(id);
        return ResponseEntity.ok(vehicleType);
    }

    @PostMapping
    public ResponseEntity<VehicleType> createVehicleType(@RequestBody VehicleType vehicleType) {
        VehicleType created = vehicleTypeService.createVehicleType(vehicleType);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<VehicleType> updateVehicleType(@PathVariable Long id, @RequestBody VehicleType vehicleType) {
        VehicleType updated = vehicleTypeService.updateVehicleType(id, vehicleType);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVehicleType(@PathVariable Long id) {
        vehicleTypeService.deleteVehicleType(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<List<VehicleType>> getVehicleTypesByCategory(@PathVariable String category) {
        List<VehicleType> vehicleTypes = vehicleTypeService.getVehicleTypesByCategory(category);
        return ResponseEntity.ok(vehicleTypes);
    }

    @GetMapping("/capacity")
    public ResponseEntity<List<VehicleType>> getVehicleTypesByCapacity(
            @RequestParam Double minWeight,
            @RequestParam Double minVolume) {
        List<VehicleType> vehicleTypes = vehicleTypeService.getVehicleTypesByLoadCapacity(minWeight, minVolume);
        return ResponseEntity.ok(vehicleTypes);
    }
}