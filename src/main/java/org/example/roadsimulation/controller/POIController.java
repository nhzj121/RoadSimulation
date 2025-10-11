// POIController.java
package org.example.roadsimulation.controller;

import org.example.roadsimulation.dto.POIDTO;
import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.service.POIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/poi")
@CrossOrigin(origins = "*") // 允许前端跨域访问
public class POIController {

    @Autowired
    private POIService poiService;

    /**
     * 接收单个POI数据并保存
     */
    @PostMapping("/save")
    public ResponseEntity<?> savePOI(@RequestBody POIDTO poiDTO) {
        try {
            POI savedPOI = poiService.savePOIFromFrontend(poiDTO);
            return ResponseEntity.ok(createSuccessResponse("POI保存成功", savedPOI));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    /**
     * 批量接收POI数据并保存
     */
    @PostMapping("/batch-save")
    public ResponseEntity<?> batchSavePOIs(@RequestBody List<POIDTO> poiDTOs) {
        try {
            List<POI> savedPOIs = poiService.batchSavePOIs(poiDTOs);
            return ResponseEntity.ok(createSuccessResponse(
                    "批量保存成功，共保存 " + savedPOIs.size() + " 个POI",
                    savedPOIs
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    /**
     * 获取所有POI
     */
    @GetMapping("/all")
    public ResponseEntity<?> getAllPOIs() {
        try {
            List<POI> pois = poiService.getAllPOIs();
            return ResponseEntity.ok(createSuccessResponse("获取POI列表成功", pois));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    /**
     * 根据类型获取POI
     */
    @GetMapping("/type/{type}")
    public ResponseEntity<?> getPOIsByType(@PathVariable String type) {
        try {
            POI.POIType poiType = POI.POIType.valueOf(type.toUpperCase());
            List<POI> pois = poiService.getPOIsByType(poiType);
            return ResponseEntity.ok(createSuccessResponse("根据类型获取POI成功", pois));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse("无效的POI类型: " + type));
        }
    }

    /**
     * 根据ID删除POI
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePOI(@PathVariable Long id) {
        try {
            poiService.deletePOI(id);
            return ResponseEntity.ok(createSuccessResponse("POI删除成功", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    /**
     * 创建成功响应
     */
    private Map<String, Object> createSuccessResponse(String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        return response;
    }

    /**
     * 创建错误响应
     */
    private Map<String, Object> createErrorResponse(String error) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", error);
        return response;
    }

    /**
     * 获取POI类型枚举
     */
    @GetMapping("/types")
    public ResponseEntity<?> getPOITypes() {
        try {
            POI.POIType[] types = POI.POIType.values();
            return ResponseEntity.ok(createSuccessResponse("获取POI类型成功", types));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }


}