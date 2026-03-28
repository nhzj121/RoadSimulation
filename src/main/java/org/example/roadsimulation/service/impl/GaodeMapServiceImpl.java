package org.example.roadsimulation.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.roadsimulation.dto.GaodeRouteRequest;
import org.example.roadsimulation.dto.GaodeRouteResponse;
import org.example.roadsimulation.service.GaodeMapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 高德地图服务实现类
 *
 * 这个类的定位：
 * - 它不是业务层
 * - 它是“高德接口适配层”
 *
 * 它只负责做 4 件事：
 * 1. 把请求参数拼成高德 API 请求
 * 2. 调用高德接口
 * 3. 解析高德响应
 * 4. 返回系统内部使用的 DTO
 *
 * 不应该在这里写：
 * - POI 查询业务
 * - Route 实体业务
 * - 数据库存取逻辑
 *
 * 后续你们会在 RoutePlanningService 中调用它。
 */
@Service
public class GaodeMapServiceImpl implements GaodeMapService {

    /**
     * 日志对象
     *
     * 为什么要用 logger：
     * - 比 System.out.println 更规范
     * - 能区分 info / debug / error
     * - 后续日志更好查
     */
    private static final Logger logger = LoggerFactory.getLogger(GaodeMapServiceImpl.class);

    /**
     * 高德路径规划 2.0 - 驾车路径规划接口
     *
     * 当前我们明确升级到 v5 驾车规划。
     */
    private static final String DRIVING_API_URL = "https://restapi.amap.com/v5/direction/driving";

    /**
     * 高德 Web API key
     */
    @Value("${gaode.api.key:your-gaode-key-here}")
    private String gaodeApiKey;

    /**
     * 高德 secret
     *
     * 目前先保留，但这版代码先不使用 sig 签名。
     * 后续如果你们确定要做签名校验，再继续加。
     */
    @Value("${gaode.api.secret:}")
    private String gaodeApiSecret;

    /**
     * Spring 的 HTTP 调用工具
     */
    private final RestTemplate restTemplate;

    /**
     * JSON 解析对象
     *
     * 不要每次解析都 new ObjectMapper()
     * 用依赖注入更合理。
     */
    private final ObjectMapper objectMapper;

    /**
     * 构造函数
     *
     * @param restTemplateBuilder 用于构建带超时设置的 RestTemplate
     * @param objectMapper Spring 容器中的 ObjectMapper
     */
    public GaodeMapServiceImpl(RestTemplateBuilder restTemplateBuilder, ObjectMapper objectMapper) {
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(10))   // 连接超时 10 秒
                .readTimeout(Duration.ofSeconds(30))      // 读取超时 30 秒
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * 驾车路径规划
     *
     * 这是当前高德适配层对外暴露的核心方法。
     *
     * @param request 前端/业务层整理后的请求参数
     * @return 路径规划响应
     */
    @Override
    public GaodeRouteResponse planDrivingRoute(GaodeRouteRequest request) {
        try {
            // 1. 构建高德请求 URL
            String url = buildDrivingRequestUrl(request);

            // 2. 打印脱敏后的 URL
            logger.info("调用高德驾车路径规划接口: {}", maskKey(url));

            // 3. 发起 HTTP GET 请求
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            // 4. 判断 HTTP 状态码
            if (response.getStatusCode() != HttpStatus.OK) {
                logger.error("高德接口 HTTP 调用失败，状态码: {}", response.getStatusCode());
                return GaodeRouteResponse.error("高德地图服务暂时不可用");
            }

            // 5. 获取响应体
            String body = response.getBody();

            // 6. debug 日志下打印完整原始响应，便于后续排查
            logger.debug("高德原始响应: {}", body);

            // 7. 解析响应
            return parseDrivingResponse(body);

        } catch (RestClientException e) {
            // HTTP 调用异常
            logger.error("调用高德接口失败", e);
            return GaodeRouteResponse.error("调用高德接口失败: " + e.getMessage());
        } catch (Exception e) {
            // 兜底异常
            logger.error("高德驾车路径规划异常", e);
            return GaodeRouteResponse.error("路线规划失败: " + e.getMessage());
        }
    }

    /**
     * 构建 v5 驾车路径规划请求 URL
     *
     * 这一版最关键的改进：
     * - 不再把参数写死
     * - 改成“有值才拼接”
     *
     * 这样后续你们新增字段时，只需要：
     * 1. 在 request 中加字段
     * 2. 在这里 addIfPresent 一行
     * 就够了
     */
    private String buildDrivingRequestUrl(GaodeRouteRequest request) {
        Map<String, String> params = new LinkedHashMap<>();

        params.put("key", gaodeApiKey);
        params.put("origin", request.getOrigin());
        params.put("destination", request.getDestination());

        addIfPresent(params, "strategy", request.getStrategy());

        return DRIVING_API_URL + "?" + buildQueryString(params);
    }



    /**
     * 只有当 value 不为空时，才加入参数
     *
     * 为什么需要这个方法：
     * - 让 URL 构建逻辑更干净
     * - 避免出现空参数
     */
    private void addIfPresent(Map<String, String> params, String key, Object value) {
        if (value == null) {
            return;
        }
        String str = String.valueOf(value).trim();
        if (!str.isEmpty()) {
            params.put(key, str);
        }
    }

    /**
     * 把参数 Map 转成 URL 查询字符串
     *
     * 例如：
     * key=xxx&origin=116.1,39.1&destination=116.2,39.2
     */
    private String buildQueryString(Map<String, String> params) {
        return params.entrySet().stream()
                .map(entry -> {
                    String key = entry.getKey();
                    String value = entry.getValue();

                    // 坐标参数不要编码，保持和浏览器里成功请求一致
                    if ("origin".equals(key) || "destination".equals(key)) {
                        return key + "=" + value;
                    }

                    return key + "=" + encode(value);
                })
                .collect(Collectors.joining("&"));
    }


    /**
     * URL 编码
     *
     * 统一用 UTF-8
     */
    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 日志脱敏：隐藏 key
     *
     * 防止把真实 key 打到日志里。
     */
    private String maskKey(String url) {
        if (gaodeApiKey == null || gaodeApiKey.isBlank()) {
            return url;
        }
        return url.replace(gaodeApiKey, "***");
    }

    /**
     * 解析高德 v5 驾车响应
     *
     * 这里是整个适配层最重要的解析入口。
     */
    private GaodeRouteResponse parseDrivingResponse(String responseBody) {
        try {
            // 1. 把 JSON 字符串转成 JsonNode
            JsonNode root = objectMapper.readTree(responseBody);

            // 2. 检查高德业务状态
            String status = root.path("status").asText();
            if (!"1".equals(status)) {
                String info = root.path("info").asText("未知错误");
                String infocode = root.path("infocode").asText();
                return GaodeRouteResponse.error("高德API错误: " + info + "(" + infocode + ")");
            }

            // 3. 取 route 节点
            JsonNode routeNode = root.path("route");

            // 4. 创建响应对象
            GaodeRouteResponse response = new GaodeRouteResponse();
            response.setSuccess(true);
            response.setMessage("路线规划成功");

            // 5. 创建内部 data 对象
            GaodeRouteResponse.GaodeRouteData data = new GaodeRouteResponse.GaodeRouteData();
            data.setOrigin(routeNode.path("origin").asText());
            data.setDestination(routeNode.path("destination").asText());

            // 6. 保留 taxi_cost，做兼容
            if (!routeNode.path("taxi_cost").isMissingNode()) {
                data.setTaxiCost(routeNode.path("taxi_cost").asDouble());
            }

            // 7. 解析 paths
            List<GaodeRouteResponse.RoutePath> pathList = new ArrayList<>();
            JsonNode pathsNode = routeNode.path("paths");

            if (pathsNode.isArray()) {
                for (JsonNode pathNode : pathsNode) {
                    pathList.add(parsePath(pathNode));
                }
            }

            // 8. 默认取第一条路径作为总览信息
            if (!pathList.isEmpty()) {
                GaodeRouteResponse.RoutePath firstPath = pathList.get(0);

                if (firstPath.getDistance() != null) {
                    data.setTotalDistance(firstPath.getDistance());
                }

                if (firstPath.getDuration() != null) {
                    data.setTotalDuration(firstPath.getDuration());
                }
            }

            // 9. 放入结果
            data.setPaths(pathList);
            response.setData(data);

            return response;

        } catch (Exception e) {
            logger.error("解析高德响应失败", e);
            return GaodeRouteResponse.error("解析路线数据失败");
        }
    }

    /**
     * 解析单条路径 path
     *
     * 一条 path 代表一个候选方案。
     */
    private GaodeRouteResponse.RoutePath parsePath(JsonNode pathNode) {
        GaodeRouteResponse.RoutePath path = new GaodeRouteResponse.RoutePath();

        // 路径总距离
        if (!pathNode.path("distance").isMissingNode()) {
            path.setDistance(pathNode.path("distance").asDouble());
        }

        // 路径总时长
        if (!pathNode.path("duration").isMissingNode()) {
            path.setDuration(pathNode.path("duration").asDouble());
        }

        // 路线策略描述
        if (!pathNode.path("strategy").isMissingNode()) {
            path.setStrategy(pathNode.path("strategy").asText());
        }

        // 收费金额
        if (!pathNode.path("tolls").isMissingNode()) {
            path.setTolls(pathNode.path("tolls").asDouble());
        }

        // 收费路段距离
        if (!pathNode.path("toll_distance").isMissingNode()) {
            path.setTollDistance(pathNode.path("toll_distance").asDouble());
        }

        // 整体折线坐标
        if (!pathNode.path("polyline").isMissingNode()) {
            path.setPolyline(pathNode.path("polyline").asText());
        }

        // 解析 steps
        List<GaodeRouteResponse.Step> stepList = new ArrayList<>();
        JsonNode stepsNode = pathNode.path("steps");
        if (stepsNode.isArray()) {
            for (JsonNode stepNode : stepsNode) {
                stepList.add(parseStep(stepNode));
            }
        }
        path.setSteps(stepList);

        return path;
    }

    /**
     * 解析单个 step
     *
     * 注意：
     * 这里做了兼容处理：
     * - 新字段：road_name / step_distance
     * - 旧字段：road / distance
     *
     * 这样你们迁移 v5 时不容易因为字段差异直接报空。
     */
    private GaodeRouteResponse.Step parseStep(JsonNode stepNode) {
        GaodeRouteResponse.Step step = new GaodeRouteResponse.Step();

        // 导航说明
        step.setInstruction(stepNode.path("instruction").asText());

        // 朝向
        step.setOrientation(stepNode.path("orientation").asText());

        // 道路名：优先读新字段 road_name
        if (!stepNode.path("road_name").isMissingNode()) {
            step.setRoad(stepNode.path("road_name").asText());
        } else {
            step.setRoad(stepNode.path("road").asText());
        }

        // 步骤距离：优先读新字段 step_distance
        if (!stepNode.path("step_distance").isMissingNode()) {
            step.setDistance(stepNode.path("step_distance").asDouble());
        } else if (!stepNode.path("distance").isMissingNode()) {
            step.setDistance(stepNode.path("distance").asDouble());
        }

        // 步骤耗时
        if (!stepNode.path("duration").isMissingNode()) {
            step.setDuration(stepNode.path("duration").asDouble());
        }

        // 步骤轨迹
        step.setPolyline(stepNode.path("polyline").asText());

        // 动作
        step.setAction(stepNode.path("action").asText());

        return step;
    }
}
