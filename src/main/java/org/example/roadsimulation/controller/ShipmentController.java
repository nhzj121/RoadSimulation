package org.example.roadsimulation.controller;

import org.example.roadsimulation.entity.Shipment;
import org.example.roadsimulation.service.ShipmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/shipments")
public class ShipmentController {

    private final ShipmentService shipmentService;

    // 1. 依赖注入：通过构造函数注入Service，这是推荐的做法
    @Autowired
    public ShipmentController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    // 2. 创建运单 - POST /api/shipments
    @PostMapping
    public ResponseEntity<Shipment> createShipment(@Valid @RequestBody Shipment shipment) {
        try {
            Shipment createdShipment = shipmentService.createShipment(shipment);
            return new ResponseEntity<>(createdShipment, HttpStatus.CREATED); // 201 Created
        } catch (IllegalArgumentException e) {
            // 处理业务规则违规（如参考号重复）
            return ResponseEntity.badRequest().build(); // 400 Bad Request
        }
    }

    // 3. 更新运单 - PUT /api/shipments/{id}
    @PutMapping("/{id}")
    public ResponseEntity<Shipment> updateShipment(
            @PathVariable Long id,
            @Valid @RequestBody Shipment shipmentDetails) {
        try {
            Shipment updatedShipment = shipmentService.updateShipment(id, shipmentDetails);
            return ResponseEntity.ok(updatedShipment); // 200 OK
        } catch (IllegalArgumentException e) {
            // 处理业务规则违规（如参考号重复）
            return ResponseEntity.badRequest().build(); // 400 Bad Request
        } catch (RuntimeException e) {
            // 处理资源不存在的情况
            return ResponseEntity.notFound().build(); // 404 Not Found
        }
    }

    // 4. 获取所有运单 - GET /api/shipments
    @GetMapping
    public ResponseEntity<List<Shipment>> getAllShipments() {
        List<Shipment> shipments = shipmentService.getAllShipments();
        return ResponseEntity.ok(shipments); // 200 OK
    }

    // 5. 分页获取运单 - GET /api/shipments/page
    @GetMapping("/page")
    public ResponseEntity<Page<Shipment>> getShipmentsPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        // 构建分页和排序参数
        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction) ?
                Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));

        Page<Shipment> shipmentPage = shipmentService.getAllShipments(pageable);
        return ResponseEntity.ok(shipmentPage); // 200 OK
    }

    // 6. 根据ID获取运单 - GET /api/shipments/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Shipment> getShipmentById(@PathVariable Long id) {
        Optional<Shipment> shipment = shipmentService.getShipmentById(id);
        return shipment.map(ResponseEntity::ok) // 200 OK
                .orElse(ResponseEntity.notFound().build()); // 404 Not Found
    }

    // 7. 根据参考号获取运单 - GET /api/shipments/ref-no/{refNo}
    @GetMapping("/ref-no/{refNo}")
    public ResponseEntity<Shipment> getShipmentByRefNo(@PathVariable String refNo) {
        Optional<Shipment> shipment = shipmentService.getShipmentByRefNo(refNo);
        return shipment.map(ResponseEntity::ok) // 200 OK
                .orElse(ResponseEntity.notFound().build()); // 404 Not Found
    }

    // 8. 根据状态查询运单 - GET /api/shipments/status/{status}
    @GetMapping("/status/{status}")
    public ResponseEntity<List<Shipment>> getShipmentsByStatus(
            @PathVariable Shipment.ShipmentStatus status) {
        List<Shipment> shipments = shipmentService.getShipmentsByStatus(status);
        return ResponseEntity.ok(shipments); // 200 OK
    }

    // 9. 根据客户ID查询运单 - GET /api/shipments/customer/{customerId}
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<Shipment>> getShipmentsByCustomerId(
            @PathVariable Long customerId) {
        List<Shipment> shipments = shipmentService.getShipmentsByCustomerId(customerId);
        return ResponseEntity.ok(shipments); // 200 OK
    }

    // 10. 综合查询 - GET /api/shipments/search
    @GetMapping("/search")
    public ResponseEntity<Page<Shipment>> searchShipments(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Shipment.ShipmentStatus status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        // 转换日期参数
        LocalDateTime startDateTime = null;
        LocalDateTime endDateTime = null;

        try {
            if (startDate != null) {
                startDateTime = LocalDateTime.parse(startDate);
                // 这是一个需要特别注意的地方。HTTP 请求中的日期通常是字符串。
                // 这里直接使用 LocalDateTime.parse() 期望的是 ISO-8601 格式（如 "2011-12-03T10:15:30"）。
                // 在实际项目中，这可能不够灵活。 ToDo
            }
            if (endDate != null) {
                endDateTime = LocalDateTime.parse(endDate);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().build(); // 400 Bad Request - 日期格式错误
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<Shipment> result = shipmentService.searchShipments(
                customerId, status, startDateTime, endDateTime, pageable);

        return ResponseEntity.ok(result); // 200 OK
    }

    // 11. 更新运单状态 - PATCH /api/shipments/{id}/status
    @PatchMapping("/{id}/status")
    public ResponseEntity<Shipment> updateShipmentStatus(
            @PathVariable Long id,
            @RequestParam Shipment.ShipmentStatus status) {
        try {
            Shipment updatedShipment = shipmentService.updateStatus(id, status);
            return ResponseEntity.ok(updatedShipment); // 200 OK
        } catch (IllegalStateException e) {
            // 处理状态转换不允许的情况
            return ResponseEntity.badRequest().body(null); // 400 Bad Request
        } catch (RuntimeException e) {
            // 处理资源不存在的情况
            return ResponseEntity.notFound().build(); // 404 Not Found
        }
    }

    // 12. 删除运单 - DELETE /api/shipments/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteShipment(@PathVariable Long id) {
        try {
            shipmentService.deleteShipment(id);
            return ResponseEntity.noContent().build(); // 204 No Content
        } catch (IllegalStateException e) {
            // 处理不允许删除的情况（如存在关联）
            return ResponseEntity.status(HttpStatus.CONFLICT).build(); // 409 Conflict
        } catch (RuntimeException e) {
            // 处理资源不存在的情况
            return ResponseEntity.notFound().build(); // 404 Not Found
        }
    }

    // 13. 检查参考号是否存在 - GET /api/shipments/exists/{refNo}
    @GetMapping("/exists/{refNo}")
    public ResponseEntity<Boolean> checkRefNoExists(@PathVariable String refNo) {
        boolean exists = shipmentService.existsByRefNo(refNo);
        return ResponseEntity.ok(exists); // 200 OK
    }
}