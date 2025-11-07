package org.example.roadsimulation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GaodeRouteRequest {

    @NotBlank(message = "起点坐标不能为空")
    private String origin;        // 起点经纬度，格式："经度,纬度" 如："116.397428,39.90923"

    @NotBlank(message = "终点坐标不能为空")
    private String destination;   // 终点经纬度，格式："经度,纬度"

    private String strategy = "0";      // 路线策略：0-最快 1-最经济 2-最短 3-避开高速
    private String extensions = "base"; // 返回结果基础/全部：base/all

    public GaodeRouteRequest() {}

    public GaodeRouteRequest(String origin, String destination) {
        this.origin = origin;
        this.destination = destination;
    }

    public GaodeRouteRequest(String origin, String destination, String strategy) {
        this(origin, destination);
        this.strategy = strategy;
    }
}