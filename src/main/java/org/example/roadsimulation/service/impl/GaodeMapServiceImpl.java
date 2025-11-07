package org.example.roadsimulation.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.dto.GaodeRouteRequest;
import org.example.roadsimulation.dto.GaodeRouteResponse;
import org.example.roadsimulation.service.GaodeMapService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GaodeMapServiceImpl implements GaodeMapService {

    @Value("${gaode.api.key:your-gaode-key-here}")
    private String gaodeApiKey;

    @Value("${gaode.api.secret:your-gaode-secret-here}")
    private String gaodeApiSecret;

    private final RestTemplate restTemplate;

    public GaodeMapServiceImpl(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public GaodeRouteResponse planDrivingRoute(GaodeRouteRequest request) {
        try {
            String url = buildRequestUrl("driving", request);
            System.out.println("调用高德API: " + url.replace(gaodeApiKey, "***"));

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                return parseGaodeResponse(response.getBody());
            } else {
                System.err.println("高德API调用失败: " + response.getStatusCode());
                return GaodeRouteResponse.error("地图服务暂时不可用");
            }
        } catch (Exception e) {
            System.err.println("调用高德路线规划失败: " + e.getMessage());
            return GaodeRouteResponse.error("路线规划失败: " + e.getMessage());
        }
    }

    private String buildRequestUrl(String routeType, GaodeRouteRequest request) {
        String baseUrl = "https://restapi.amap.com/v3/direction/" + routeType;

        Map<String, String> params = new LinkedHashMap<>();
        params.put("key", gaodeApiKey);
        params.put("origin", request.getOrigin());
        params.put("destination", request.getDestination());
        params.put("strategy", request.getStrategy());
        params.put("extensions", request.getExtensions());
        params.put("output", "JSON");



        return baseUrl + "?" + buildQueryString(params);
    }



    private String buildQueryString(Map<String, String> params) {
        return params.entrySet().stream()
                .map(entry -> {
                    String value = entry.getValue();
                    // 对于坐标参数，完全不进行URL编码
                    if ("origin".equals(entry.getKey()) || "destination".equals(entry.getKey())) {
                        return entry.getKey() + "=" + value;
                    } else {
                        return entry.getKey() + "=" + encode(value);
                    }
                })
                .collect(Collectors.joining("&"));
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }

    private GaodeRouteResponse parseGaodeResponse(String responseBody) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(responseBody);

            String status = root.path("status").asText();
            if (!"1".equals(status)) {
                String info = root.path("info").asText("未知错误");
                String infocode = root.path("infocode").asText();
                return GaodeRouteResponse.error("高德API错误: " + info + "(" + infocode + ")");
            }

            GaodeRouteResponse response = new GaodeRouteResponse();
            response.setSuccess(true);
            response.setMessage("路线规划成功");

            GaodeRouteResponse.GaodeRouteData data = new GaodeRouteResponse.GaodeRouteData();

            JsonNode routeNode = root.path("route");
            data.setOrigin(routeNode.path("origin").asText());
            data.setDestination(routeNode.path("destination").asText());
            data.setTaxiCost(routeNode.path("taxi_cost").asDouble());

            JsonNode paths = routeNode.path("paths");
            List<GaodeRouteResponse.RoutePath> pathList = new ArrayList<>();

            if (paths.isArray() && paths.size() > 0) {
                for (JsonNode pathNode : paths) {
                    GaodeRouteResponse.RoutePath path = parseRoutePath(pathNode);
                    pathList.add(path);
                }

                GaodeRouteResponse.RoutePath firstPath = pathList.get(0);
                data.setTotalDistance(firstPath.getDistance().intValue());
                data.setTotalDuration(firstPath.getDuration().intValue());
            }

            data.setPaths(pathList);
            response.setData(data);

            return response;
        } catch (Exception e) {
            System.err.println("解析高德响应失败: " + e.getMessage());
            return GaodeRouteResponse.error("解析路线数据失败");
        }
    }

    private GaodeRouteResponse.RoutePath parseRoutePath(JsonNode pathNode) {
        GaodeRouteResponse.RoutePath path = new GaodeRouteResponse.RoutePath();
        path.setDistance(pathNode.path("distance").asDouble());
        path.setDuration(pathNode.path("duration").asDouble());
        path.setStrategy(pathNode.path("strategy").asText());
        path.setTolls(pathNode.path("tolls").asDouble());
        path.setTollDistance(pathNode.path("toll_distance").asDouble());
        path.setPolyline(pathNode.path("polyline").asText());

        JsonNode steps = pathNode.path("steps");
        List<GaodeRouteResponse.Step> stepList = new ArrayList<>();
        if (steps.isArray()) {
            for (JsonNode stepNode : steps) {
                GaodeRouteResponse.Step step = new GaodeRouteResponse.Step();
                step.setInstruction(stepNode.path("instruction").asText());
                step.setRoad(stepNode.path("road").asText());
                step.setDistance(stepNode.path("distance").asDouble());
                step.setDuration(stepNode.path("duration").asDouble());
                step.setPolyline(stepNode.path("polyline").asText());
                step.setAction(stepNode.path("action").asText());
                step.setOrientation(stepNode.path("orientation").asText());
                stepList.add(step);
            }
        }
        path.setSteps(stepList);

        return path;
    }
}