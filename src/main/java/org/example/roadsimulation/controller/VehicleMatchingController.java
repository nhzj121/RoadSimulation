package org.example.roadsimulation.controller;

import org.example.roadsimulation.dto.VehicleMatchingRequest;
import org.example.roadsimulation.dto.VehicleMatchingResult;
import org.example.roadsimulation.service.VehicleMatchingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vehicle-matching")
public class VehicleMatchingController {

    @Autowired
    private VehicleMatchingService vehicleMatchingService;

    @PostMapping("/match")
    public ResponseEntity<VehicleMatchingResult> matchVehicle(@RequestBody VehicleMatchingRequest request) {
        VehicleMatchingResult result = vehicleMatchingService.matchVehicleForGoods(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/quick-match")
    public ResponseEntity<VehicleMatchingResult> quickMatch(
            @RequestParam Long goodsId,
            @RequestParam Integer quantity) {
        VehicleMatchingResult result = vehicleMatchingService.quickMatch(goodsId, quantity);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/batch-match")
    public ResponseEntity<List<VehicleMatchingResult>> batchMatch(@RequestBody List<VehicleMatchingRequest> requests) {
        List<VehicleMatchingResult> results = vehicleMatchingService.batchMatchVehicles(requests);
        return ResponseEntity.ok(results);
    }
}