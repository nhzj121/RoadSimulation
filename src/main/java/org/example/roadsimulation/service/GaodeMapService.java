package org.example.roadsimulation.service;

import org.example.roadsimulation.dto.GaodeRouteRequest;
import org.example.roadsimulation.dto.GaodeRouteResponse;

public interface GaodeMapService {

    /**
     * 规划驾车路线
     */
    GaodeRouteResponse planDrivingRoute(GaodeRouteRequest request);

}