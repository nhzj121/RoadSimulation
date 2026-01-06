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

/**
 * 高德地图服务实现类
 * 负责调用高德地图API进行路线规划等操作
 */
@Service
public class GaodeMapServiceImpl implements GaodeMapService {

    // 高德地图API密钥，从配置文件中注入，默认值为"your-gaode-key-here"
    @Value("${gaode.api.key:your-gaode-key-here}")
    private String gaodeApiKey;

    // 高德地图API密钥（可选，用于签名验证），从配置文件中注入，默认值为"your-gaode-secret-here"
    @Value("${gaode.api.secret:your-gaode-secret-here}")
    private String gaodeApiSecret;

    // REST模板，用于发送HTTP请求到高德地图API
    private final RestTemplate restTemplate;

    /**
     * 构造函数，通过RestTemplateBuilder创建配置好的RestTemplate实例
     *
     * @param restTemplateBuilder Spring Boot提供的RestTemplate构建器
     */
    public GaodeMapServiceImpl(RestTemplateBuilder restTemplateBuilder) {
        // 构建RestTemplate实例，配置连接和读取超时时间
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(10))  // 设置连接超时时间为10秒
                .readTimeout(Duration.ofSeconds(30))     // 设置读取超时时间为30秒
                .build();
    }

    /**
     * 规划驾车路线 - 核心业务方法
     * 调用高德地图驾车路线规划API，获取从起点到终点的驾车路线信息
     *
     * @param request 路线规划请求参数，包含起点、终点、策略等信息
     * @return GaodeRouteResponse 路线规划响应结果
     */
    @Override
    public GaodeRouteResponse planDrivingRoute(GaodeRouteRequest request) {
        try {
            // 构建高德地图API请求URL
            String url = buildRequestUrl("driving", request);
            // 打印API调用日志（隐藏敏感信息）
            System.out.println("调用高德API: " + url.replace(gaodeApiKey, "***"));

            // 发送GET请求到高德地图API
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            // 检查HTTP响应状态码
            if (response.getStatusCode() == HttpStatus.OK) {
                // 成功响应，解析返回的JSON数据
                return parseGaodeResponse(response.getBody());
            } else {
                // HTTP状态码非200，记录错误日志
                System.err.println("高德API调用失败: " + response.getStatusCode());
                return GaodeRouteResponse.error("地图服务暂时不可用");
            }
        } catch (Exception e) {
            // 捕获所有异常，记录错误日志并返回错误响应
            System.err.println("调用高德路线规划失败: " + e.getMessage());
            return GaodeRouteResponse.error("路线规划失败: " + e.getMessage());
        }
    }

    /**
     * 构建高德地图API请求URL
     * 根据路线类型和请求参数生成完整的API调用URL
     *
     * @param routeType 路线类型，如"driving"（驾车）、"walking"（步行）等
     * @param request 路线规划请求参数
     * @return String 完整的API请求URL
     */
    private String buildRequestUrl(String routeType, GaodeRouteRequest request) {
        // 高德地图API基础URL，根据路线类型动态拼接
        String baseUrl = "https://restapi.amap.com/v3/direction/" + routeType;

        // 使用LinkedHashMap保持参数顺序（对于签名验证很重要）
        Map<String, String> params = new LinkedHashMap<>();
        // 设置API请求参数
        params.put("key", gaodeApiKey);                    // API密钥
        params.put("origin", request.getOrigin());         // 起点坐标
        params.put("destination", request.getDestination()); // 终点坐标
        params.put("strategy", request.getStrategy());     // 路线策略
        params.put("extensions", request.getExtensions()); // 返回结果详略程度
        params.put("output", "JSON");                      // 返回格式

        // 拼接基础URL和查询参数
        return baseUrl + "?" + buildQueryString(params);
    }

    /**
     * 构建查询参数字符串
     * 将参数Map转换为URL查询字符串格式
     *
     * @param params 参数Map，key-value形式
     * @return String URL编码后的查询字符串
     */
    private String buildQueryString(Map<String, String> params) {
        return params.entrySet().stream()
                .map(entry -> {
                    String value = entry.getValue();
                    // 对于坐标参数（起点、终点），不进行URL编码，因为坐标格式特殊
                    if ("origin".equals(entry.getKey()) || "destination".equals(entry.getKey())) {
                        return entry.getKey() + "=" + value;
                    } else {
                        // 其他参数进行URL编码
                        return entry.getKey() + "=" + encode(value);
                    }
                })
                .collect(Collectors.joining("&"));  // 用"&"连接所有参数
    }

    /**
     * URL编码工具方法
     * 对字符串进行URL编码，使用UTF-8字符集
     *
     * @param value 需要编码的字符串
     * @return String 编码后的字符串
     */
    private String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            // 编码失败时返回原值（理论上不会发生，因为UTF-8是标准字符集）
            return value;
        }
    }

    /**
     * 解析高德地图API响应
     * 将高德返回的JSON字符串解析为业务对象
     *
     * @param responseBody 高德API返回的JSON字符串
     * @return GaodeRouteResponse 解析后的路线规划响应对象
     */
    private GaodeRouteResponse parseGaodeResponse(String responseBody) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            // 将JSON字符串解析为JsonNode对象，便于遍历
            JsonNode root = mapper.readTree(responseBody);

            // 检查API响应状态，"1"表示成功，其他值表示失败
            String status = root.path("status").asText();
            if (!"1".equals(status)) {
                // API返回错误，提取错误信息
                String info = root.path("info").asText("未知错误");
                String infocode = root.path("infocode").asText();
                return GaodeRouteResponse.error("高德API错误: " + info + "(" + infocode + ")");
            }

            // 创建成功响应对象
            GaodeRouteResponse response = new GaodeRouteResponse();
            response.setSuccess(true);
            response.setMessage("路线规划成功");

            // 创建路线数据对象
            GaodeRouteResponse.GaodeRouteData data = new GaodeRouteResponse.GaodeRouteData();

            // 解析route节点
            JsonNode routeNode = root.path("route");
            data.setOrigin(routeNode.path("origin").asText());           // 起点信息
            data.setDestination(routeNode.path("destination").asText()); // 终点信息
            data.setTaxiCost(routeNode.path("taxi_cost").asDouble());    // 出租车费用估算

            // 解析paths节点，包含多条可选路线
            JsonNode paths = routeNode.path("paths");
            List<GaodeRouteResponse.RoutePath> pathList = new ArrayList<>();

            // 检查paths是否为数组且包含数据
            if (paths.isArray() && paths.size() > 0) {
                // 遍历所有路线
                for (JsonNode pathNode : paths) {
                    GaodeRouteResponse.RoutePath path = parseRoutePath(pathNode);
                    pathList.add(path);
                }

                // 默认使用第一条路线作为主要路线，设置总距离和总时长
                GaodeRouteResponse.RoutePath firstPath = pathList.get(0);
                data.setTotalDistance(firstPath.getDistance().intValue());  // 总距离（米）
                data.setTotalDuration(firstPath.getDuration().intValue());  // 总时长（秒）
            }

            // 设置路线列表到数据对象
            data.setPaths(pathList);
            response.setData(data);

            return response;
        } catch (Exception e) {
            // JSON解析异常，返回错误响应
            System.err.println("解析高德响应失败: " + e.getMessage());
            return GaodeRouteResponse.error("解析路线数据失败");
        }
    }

    /**
     * 解析单条路线路径信息
     * 将JSON节点解析为RoutePath对象
     *
     * @param pathNode 路线路径的JSON节点
     * @return RoutePath 解析后的路线路径对象
     */
    private GaodeRouteResponse.RoutePath parseRoutePath(JsonNode pathNode) {
        GaodeRouteResponse.RoutePath path = new GaodeRouteResponse.RoutePath();
        // 设置路线基本信息
        path.setDistance(pathNode.path("distance").asDouble());          // 路线总距离
        path.setDuration(pathNode.path("duration").asDouble());          // 路线预计时间
        path.setStrategy(pathNode.path("strategy").asText());            // 路线策略
        path.setTolls(pathNode.path("tolls").asDouble());                // 收费金额
        path.setTollDistance(pathNode.path("toll_distance").asDouble()); // 收费路段距离
        path.setPolyline(pathNode.path("polyline").asText());            // 路线坐标点串

        // 解析steps节点，包含路线的详细步骤
        JsonNode steps = pathNode.path("steps");
        List<GaodeRouteResponse.Step> stepList = new ArrayList<>();
        if (steps.isArray()) {
            // 遍历所有步骤
            for (JsonNode stepNode : steps) {
                GaodeRouteResponse.Step step = new GaodeRouteResponse.Step();
                step.setInstruction(stepNode.path("instruction").asText()); // 步骤说明
                step.setRoad(stepNode.path("road").asText());               // 道路名称
                step.setDistance(stepNode.path("distance").asDouble());     // 步骤距离
                step.setDuration(stepNode.path("duration").asDouble());     // 步骤时间
                step.setPolyline(stepNode.path("polyline").asText());       // 步骤坐标点
                step.setAction(stepNode.path("action").asText());           // 动作指令
                step.setOrientation(stepNode.path("orientation").asText()); // 方向
                stepList.add(step);
            }
        }
        path.setSteps(stepList);

        return path;
    }
}