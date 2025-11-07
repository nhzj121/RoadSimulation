package org.example.roadsimulation.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

@Service
public class GaodeDiagnosticService {

    @Value("${gaode.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate;

    public GaodeDiagnosticService() {
        this.restTemplate = new RestTemplate();
    }

    public void diagnoseApiKey() {
        System.out.println("=== 高德API密钥诊断 ===");
        System.out.println("API Key: " + apiKey.substring(0, Math.min(8, apiKey.length())) + "..." + apiKey.substring(apiKey.length() - 4));

        // 测试1：基础路径规划
        testBasicRouting();

        // 测试2：IP定位（验证Key是否有效）
        testIpLocation();

        // 测试3：地理编码（验证Key是否有效）
        testGeocode();
    }

    private void testBasicRouting() {
        try {
            String url = "https://restapi.amap.com/v3/direction/driving?" +
                    "key=" + apiKey +
                    "&origin=116.397428,39.90923" +
                    "&destination=116.407428,39.91923" +
                    "&strategy=0";

            System.out.println("\n1. 测试路径规划API:");
            System.out.println("URL: " + url.replace(apiKey, "***"));

            ResponseEntity<String> response = restTemplate.getForEntity(new URI(url), String.class);
            String responseBody = response.getBody();

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(responseBody);

            String status = root.path("status").asText();
            String info = root.path("info").asText();
            String infocode = root.path("infocode").asText();

            System.out.println("状态: " + status);
            System.out.println("信息: " + info);
            System.out.println("代码: " + infocode);

            if ("1".equals(status)) {
                System.out.println("✅ 路径规划API调用成功");
            } else {
                System.out.println("❌ 路径规划API调用失败: " + info + "(" + infocode + ")");
            }

        } catch (Exception e) {
            System.out.println("❌ 路径规划测试异常: " + e.getMessage());
        }
    }

    private void testIpLocation() {
        try {
            String url = "https://restapi.amap.com/v3/ip?key=" + apiKey;

            System.out.println("\n2. 测试IP定位API:");
            System.out.println("URL: " + url.replace(apiKey, "***"));

            ResponseEntity<String> response = restTemplate.getForEntity(new URI(url), String.class);
            String responseBody = response.getBody();

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(responseBody);

            String status = root.path("status").asText();
            String info = root.path("info").asText();

            System.out.println("状态: " + status);
            System.out.println("信息: " + info);

            if ("1".equals(status)) {
                System.out.println("✅ IP定位API调用成功");
                System.out.println("位置: " + root.path("province").asText() + " " + root.path("city").asText());
            } else {
                System.out.println("❌ IP定位API调用失败: " + info);
            }

        } catch (Exception e) {
            System.out.println("❌ IP定位测试异常: " + e.getMessage());
        }
    }

    private void testGeocode() {
        try {
            String url = "https://restapi.amap.com/v3/geocode/geo?key=" + apiKey + "&address=天安门";

            System.out.println("\n3. 测试地理编码API:");
            System.out.println("URL: " + url.replace(apiKey, "***"));

            ResponseEntity<String> response = restTemplate.getForEntity(new URI(url), String.class);
            String responseBody = response.getBody();

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(responseBody);

            String status = root.path("status").asText();
            String info = root.path("info").asText();
            String count = root.path("count").asText();

            System.out.println("状态: " + status);
            System.out.println("信息: " + info);
            System.out.println("结果数: " + count);

            if ("1".equals(status)) {
                System.out.println("✅ 地理编码API调用成功");
            } else {
                System.out.println("❌ 地理编码API调用失败: " + info);
            }

        } catch (Exception e) {
            System.out.println("❌ 地理编码测试异常: " + e.getMessage());
        }
    }
}