package org.example.roadsimulation.service;

import org.example.roadsimulation.dto.VehicleMatchingRequest;
import org.example.roadsimulation.dto.VehicleMatchingResult;

import java.util.List;

public interface VehicleMatchingService {

    VehicleMatchingResult matchVehicleForGoods(VehicleMatchingRequest request);

    List<VehicleMatchingResult> batchMatchVehicles(List<VehicleMatchingRequest> requests);

    VehicleMatchingResult quickMatch(Long goodsId, Integer quantity);
}