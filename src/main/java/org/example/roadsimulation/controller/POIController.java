package org.example.roadsimulation.controller;

import org.example.roadsimulation.entity.POI;
import org.example.roadsimulation.service.POIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

// 使用 jakarta.validation 而不是 javax.validation
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
/**
 * POI Controller
 *
 * 功能：
 * 1. 提供完整的 RESTful API 用于 POI 管理
 * 2. 支持 CRUD 操作、分页查询、条件查询
 * 3. 统一的响应格式和异常处理
 * 4. 参数校验和业务逻辑验证
 */
@RestController
@RequestMapping("/api/pois")
@CrossOrigin(origins = "*", maxAge = 3600)
@Validated
public class POIController {

    private final POIService poiService;

    @Autowired
    public POIController(POIService poiService) {
        this.poiService = poiService;
    }

    /**
     * 创建新的 POI
     */
    @PostMapping
    public ResponseEntity<ApiResponse<POI>> createPOI(@Valid @RequestBody CreatePOIRequest request) {
        try {
            POI poi = new POI(request.getName(), request.getLongitude(), request.getLatitude(), request.getPoiType());
            POI savedPOI = poiService.create(poi);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("POI 创建成功", savedPOI));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 根据 ID 获取 POI 详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<POI>> getPOIById(@PathVariable @Min(1) Long id) {
        Optional<POI> poi = poiService.getById(id);
        return poi.map(value -> ResponseEntity.ok(ApiResponse.success("查询成功", value)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("POI 不存在，ID: " + id)));
    }

    /**
     * 更新 POI 信息
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<POI>> updatePOI(
            @PathVariable @Min(1) Long id,
            @Valid @RequestBody UpdatePOIRequest request) {
        try {
            POI poiDetails = new POI(request.getName(), request.getLongitude(), request.getLatitude(), request.getPoiType());
            poiDetails.setId(id); // 确保 ID 一致

            POI updatedPOI = poiService.update(id, poiDetails);
            return ResponseEntity.ok(ApiResponse.success("POI 更新成功", updatedPOI));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 删除 POI
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePOI(@PathVariable @Min(1) Long id) {
        try {
            poiService.delete(id);
            return ResponseEntity.ok(ApiResponse.success("POI 删除成功", null));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 获取所有 POI 列表
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<POI>>> getAllPOIs() {
        List<POI> pois = poiService.getAll();
        return ResponseEntity.ok(ApiResponse.success("查询成功", pois));
    }

    /**
     * 分页查询 POI
     */
    @GetMapping("/page")
    public ResponseEntity<ApiResponse<Page<POI>>> getPOIsByPage(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {

        Sort sort = direction.equalsIgnoreCase("desc") ?
                Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<POI> poiPage = poiService.getAll(pageable);
        return ResponseEntity.ok(ApiResponse.success("分页查询成功", poiPage));
    }

    /**
     * 根据名称模糊搜索 POI
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<POI>>> searchPOIsByName(
            @RequestParam @NotBlank String name) {
        List<POI> pois = poiService.searchByName(name);
        return ResponseEntity.ok(ApiResponse.success("搜索成功", pois));
    }

    /**
     * 根据类型查询 POI
     */
    @GetMapping("/type/{type}")
    public ResponseEntity<ApiResponse<List<POI>>> getPOIsByType(
            @PathVariable @NotNull POI.POIType type) {
        List<POI> pois = poiService.findByType(type);
        return ResponseEntity.ok(ApiResponse.success("查询成功", pois));
    }

    /**
     * 检查 POI 名称是否存在
     */
    @GetMapping("/check-name")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> checkNameExists(
            @RequestParam @NotBlank String name) {
        boolean exists = poiService.existsByName(name);
        Map<String, Boolean> result = new HashMap<>();
        result.put("exists", exists);
        return ResponseEntity.ok(ApiResponse.success("检查完成", result));
    }

    /**
     * 获取所有 POI 类型枚举值
     */
    @GetMapping("/types")
    public ResponseEntity<ApiResponse<POI.POIType[]>> getPOITypes() {
        POI.POIType[] types = POI.POIType.values();
        return ResponseEntity.ok(ApiResponse.success("获取类型成功", types));
    }

    /**
     * 批量创建 POI（扩展功能）
     */
    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<List<POI>>> createPOIsBatch(
            @Valid @RequestBody List<CreatePOIRequest> requests) {
        try {
            List<POI> createdPOIs = requests.stream()
                    .map(request -> {
                        POI poi = new POI(request.getName(), request.getLongitude(),
                                request.getLatitude(), request.getPoiType());
                        return poiService.create(poi);
                    })
                    .collect(java.util.stream.Collectors.toList());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("批量创建 POI 成功", createdPOIs));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 统计各类型 POI 数量（扩展功能）
     */
    @GetMapping("/statistics/type-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getPOITypeStatistics() {
        Map<String, Long> statistics = new HashMap<>();
        for (POI.POIType type : POI.POIType.values()) {
            List<POI> pois = poiService.findByType(type);
            statistics.put(type.name(), (long) pois.size());
        }
        return ResponseEntity.ok(ApiResponse.success("统计查询成功", statistics));
    }

    // ================= DTO 类 =================

    /**
     * 创建 POI 请求 DTO
     */
    public static class CreatePOIRequest {
        @NotBlank(message = "POI 名称不能为空")
        private String name;

        @NotNull(message = "经度不能为空")
        private Double longitude;

        @NotNull(message = "纬度不能为空")
        private Double latitude;

        @NotNull(message = "POI 类型不能为空")
        private POI.POIType poiType;

        // Getter 方法
        public String getName() { return name; }
        public Double getLongitude() { return longitude; }
        public Double getLatitude() { return latitude; }
        public POI.POIType getPoiType() { return poiType; }

        // Setter 方法（用于 JSON 反序列化）
        public void setName(String name) { this.name = name; }
        public void setLongitude(Double longitude) { this.longitude = longitude; }
        public void setLatitude(Double latitude) { this.latitude = latitude; }
        public void setPoiType(POI.POIType poiType) { this.poiType = poiType; }
    }

    /**
     * 更新 POI 请求 DTO
     */
    public static class UpdatePOIRequest {
        @NotBlank(message = "POI 名称不能为空")
        private String name;

        @NotNull(message = "经度不能为空")
        private Double longitude;

        @NotNull(message = "纬度不能为空")
        private Double latitude;

        @NotNull(message = "POI 类型不能为空")
        private POI.POIType poiType;

        // Getter 方法
        public String getName() { return name; }
        public Double getLongitude() { return longitude; }
        public Double getLatitude() { return latitude; }
        public POI.POIType getPoiType() { return poiType; }

        // Setter 方法（用于 JSON 反序列化）
        public void setName(String name) { this.name = name; }
        public void setLongitude(Double longitude) { this.longitude = longitude; }
        public void setLatitude(Double latitude) { this.latitude = latitude; }
        public void setPoiType(POI.POIType poiType) { this.poiType = poiType; }
    }

    /**
     * 统一 API 响应格式
     */
    public static class ApiResponse<T> {
        private final boolean success;
        private final String message;
        private final T data;
        private final long timestamp;

        private ApiResponse(boolean success, String message, T data) {
            this.success = success;
            this.message = message;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        public static <T> ApiResponse<T> success(String message, T data) {
            return new ApiResponse<>(true, message, data);
        }

        public static <T> ApiResponse<T> error(String message) {
            return new ApiResponse<>(false, message, null);
        }

        // Getter 方法
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public T getData() { return data; }
        public long getTimestamp() { return timestamp; }
    }

    /**
     * 全局异常处理
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Object>> handleRuntimeException(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("服务器内部错误: " + e.getMessage()));
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolationException(
            jakarta.validation.ConstraintViolationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("参数校验失败: " + e.getMessage()));
    }
}