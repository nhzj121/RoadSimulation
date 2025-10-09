package org.example.roadsimulation.controller;

import org.example.roadsimulation.entity.Driver;
import org.example.roadsimulation.service.DriverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Driver Controller
 *
 * 功能：
 * 1. 提供完整的 RESTful API 用于司机管理
 * 2. 支持 CRUD 操作、分页查询、条件查询
 * 3. 统一的响应格式和异常处理
 * 4. 支持司机状态管理、车辆分配查询等扩展功能
 */
@RestController
@RequestMapping("/api/drivers")
@CrossOrigin(origins = "*", maxAge = 3600)
public class DriverController {

    private static final Logger logger = LoggerFactory.getLogger(DriverController.class);

    private final DriverService driverService;

    @Autowired
    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    /**
     * 创建新司机
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Driver>> createDriver(@RequestBody Driver driver) {
        logger.info("创建新司机: {}", driver.getDriverName());

        try {
            Driver savedDriver = driverService.createDriver(driver);
            logger.info("司机创建成功，ID: {}, 姓名: {}", savedDriver.getId(), savedDriver.getDriverName());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("司机创建成功", savedDriver));
        } catch (IllegalArgumentException e) {
            logger.error("司机创建失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 更新司机信息
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Driver>> updateDriver(
            @PathVariable Long id,
            @RequestBody Driver driverDetails) {
        logger.info("更新司机信息，ID: {}", id);

        try {
            Driver updatedDriver = driverService.updateDriver(id, driverDetails);
            logger.info("司机信息更新成功，ID: {}", id);
            return ResponseEntity.ok(ApiResponse.success("司机信息更新成功", updatedDriver));
        } catch (IllegalArgumentException e) {
            logger.error("司机信息更新失败，ID: {}, 错误: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (RuntimeException e) {
            logger.error("司机信息更新失败，ID: {}, 错误: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 根据ID获取司机详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Driver>> getDriverById(@PathVariable Long id) {
        logger.info("查询司机详情，ID: {}", id);

        Optional<Driver> driver = driverService.getDriverById(id);
        return driver.map(value -> {
                    logger.info("司机查询成功，ID: {}", id);
                    return ResponseEntity.ok(ApiResponse.success("查询成功", value));
                })
                .orElseGet(() -> {
                    logger.warn("司机不存在，ID: {}", id);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(ApiResponse.error("司机不存在，ID: " + id));
                });
    }

    /**
     * 获取所有司机列表
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Driver>>> getAllDrivers() {
        logger.info("获取所有司机列表");

        List<Driver> drivers = driverService.getAllDrivers();
        logger.info("获取到 {} 个司机", drivers.size());
        return ResponseEntity.ok(ApiResponse.success("查询成功", drivers));
    }

    /**
     * 分页查询司机
     */
    @GetMapping("/page")
    public ResponseEntity<ApiResponse<Page<Driver>>> getDriversByPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {

        logger.info("分页查询司机，页码: {}, 每页大小: {}, 排序: {}, 方向: {}", page, size, sortBy, direction);

        Sort sort = direction.equalsIgnoreCase("desc") ?
                Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Driver> driverPage = driverService.getAllDrivers(pageable);
        logger.info("分页查询成功，总记录数: {}, 总页数: {}", driverPage.getTotalElements(), driverPage.getTotalPages());
        return ResponseEntity.ok(ApiResponse.success("分页查询成功", driverPage));
    }

    /**
     * 根据手机号查询司机
     */
    @GetMapping("/phone/{phone}")
    public ResponseEntity<ApiResponse<Driver>> getDriverByPhone(@PathVariable String phone) {
        logger.info("根据手机号查询司机: {}", phone);

        Optional<Driver> driver = driverService.getDriverByPhone(phone);
        return driver.map(value -> {
                    logger.info("手机号查询成功: {}", phone);
                    return ResponseEntity.ok(ApiResponse.success("查询成功", value));
                })
                .orElseGet(() -> {
                    logger.warn("手机号不存在: {}", phone);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(ApiResponse.error("手机号不存在: " + phone));
                });
    }

    /**
     * 根据姓名模糊搜索司机
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<Driver>>> searchDriversByName(@RequestParam String name) {
        logger.info("根据姓名搜索司机: {}", name);

        List<Driver> drivers = driverService.searchDriversByName(name);
        logger.info("姓名搜索成功，找到 {} 个司机", drivers.size());
        return ResponseEntity.ok(ApiResponse.success("搜索成功", drivers));
    }

    /**
     * 根据状态查询司机
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<ApiResponse<List<Driver>>> getDriversByStatus(@PathVariable Driver.DriverStatus status) {
        logger.info("根据状态查询司机: {}", status);

        List<Driver> drivers = driverService.getDriversByStatus(status);
        logger.info("状态查询成功，找到 {} 个 {} 状态的司机", drivers.size(), status);
        return ResponseEntity.ok(ApiResponse.success("查询成功", drivers));
    }

    /**
     * 删除司机
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDriver(@PathVariable Long id) {
        logger.info("删除司机，ID: {}", id);

        try {
            driverService.deleteDriver(id);
            logger.info("司机删除成功，ID: {}", id);
            return ResponseEntity.ok(ApiResponse.success("司机删除成功", null));
        } catch (IllegalStateException e) {
            logger.error("司机删除失败，ID: {}, 错误: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (RuntimeException e) {
            logger.error("司机删除失败，ID: {}, 错误: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 检查手机号是否存在
     */
    @GetMapping("/check-phone")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> checkPhoneExists(@RequestParam String phone) {
        logger.info("检查手机号是否存在: {}", phone);

        boolean exists = driverService.existsByPhone(phone);
        Map<String, Boolean> result = new HashMap<>();
        result.put("exists", exists);

        logger.info("手机号检查完成: {} -> {}", phone, exists);
        return ResponseEntity.ok(ApiResponse.success("检查完成", result));
    }

    /**
     * 获取所有司机状态枚举值
     */
    @GetMapping("/statuses")
    public ResponseEntity<ApiResponse<Driver.DriverStatus[]>> getDriverStatuses() {
        logger.info("获取司机状态枚举值");

        Driver.DriverStatus[] statuses = Driver.DriverStatus.values();
        return ResponseEntity.ok(ApiResponse.success("获取状态成功", statuses));
    }

    /**
     * 获取司机的关联车辆信息
     */
    @GetMapping("/{id}/vehicles")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDriverVehicles(@PathVariable Long id) {
        logger.info("获取司机的关联车辆，司机ID: {}", id);

        Optional<Driver> driver = driverService.getDriverById(id);
        return driver.map(value -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("driver", value.getDriverName());
                    result.put("vehicles", value.getVehicles());
                    result.put("vehicleCount", value.getVehicles().size());

                    logger.info("获取司机车辆成功，司机ID: {}, 车辆数: {}", id, value.getVehicles().size());
                    return ResponseEntity.ok(ApiResponse.success("查询成功", result));
                })
                .orElseGet(() -> {
                    logger.warn("司机不存在，ID: {}", id);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(ApiResponse.error("司机不存在，ID: " + id));
                });
    }

    /**
     * 获取司机的任务信息
     */
    @GetMapping("/{id}/assignments")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDriverAssignments(@PathVariable Long id) {
        logger.info("获取司机的任务信息，司机ID: {}", id);

        Optional<Driver> driver = driverService.getDriverById(id);
        return driver.map(value -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("driver", value.getDriverName());
                    result.put("assignments", value.getAssignments());
                    result.put("assignmentCount", value.getAssignments().size());
                    result.put("currentAssignment", value.getCurrentAssignment());

                    logger.info("获取司机任务成功，司机ID: {}, 任务数: {}", id, value.getAssignments().size());
                    return ResponseEntity.ok(ApiResponse.success("查询成功", result));
                })
                .orElseGet(() -> {
                    logger.warn("司机不存在，ID: {}", id);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(ApiResponse.error("司机不存在，ID: " + id));
                });
    }

    /**
     * 更新司机状态
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Driver>> updateDriverStatus(
            @PathVariable Long id,
            @RequestParam Driver.DriverStatus status) {
        logger.info("更新司机状态，司机ID: {}, 新状态: {}", id, status);

        Optional<Driver> driverOpt = driverService.getDriverById(id);
        if (driverOpt.isEmpty()) {
            logger.warn("司机不存在，ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("司机不存在，ID: " + id));
        }

        Driver driver = driverOpt.get();
        driver.setCurrentStatus(status);

        try {
            Driver updatedDriver = driverService.updateDriver(id, driver);
            logger.info("司机状态更新成功，ID: {}, 状态: {}", id, status);
            return ResponseEntity.ok(ApiResponse.success("状态更新成功", updatedDriver));
        } catch (Exception e) {
            logger.error("司机状态更新失败，ID: {}, 错误: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("状态更新失败: " + e.getMessage()));
        }
    }

    /**
     * 批量创建司机
     */
    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<List<Driver>>> createDriversBatch(@RequestBody List<Driver> drivers) {
        logger.info("批量创建司机，数量: {}", drivers.size());

        try {
            List<Driver> createdDrivers = drivers.stream()
                    .map(driverService::createDriver)
                    .toList();
            logger.info("批量创建司机成功，数量: {}", createdDrivers.size());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("批量创建司机成功", createdDrivers));
        } catch (IllegalArgumentException e) {
            logger.error("批量创建司机失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 统计各状态司机数量
     */
    @GetMapping("/statistics/status-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getDriverStatusStatistics() {
        logger.info("统计司机状态分布");

        Map<String, Long> statistics = new HashMap<>();
        for (Driver.DriverStatus status : Driver.DriverStatus.values()) {
            List<Driver> drivers = driverService.getDriversByStatus(status);
            statistics.put(status.name(), (long) drivers.size());
        }

        logger.info("司机状态统计完成");
        return ResponseEntity.ok(ApiResponse.success("统计查询成功", statistics));
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
        logger.error("业务异常: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Object>> handleRuntimeException(RuntimeException e) {
        logger.error("运行时异常: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("服务器内部错误: " + e.getMessage()));
    }
}