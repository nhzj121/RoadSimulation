package org.example.roadsimulation.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.util.Date;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgumentException(IllegalArgumentException ex, WebRequest request) {
        ErrorDetails errorDetails = new ErrorDetails(new Date(), ex.getMessage(), request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> handleIllegalStateException(IllegalStateException ex, WebRequest request) {
        ErrorDetails errorDetails = new ErrorDetails(new Date(), ex.getMessage(), request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(GoodsAlreadyExistsException.class)
    public ResponseEntity<?> handleGoodsAlreadyExistsException(GoodsAlreadyExistsException ex, WebRequest request) {
        GoodsErrorDetails errorDetails = new GoodsErrorDetails(
                new Date(),
                ex.getMessage(),
                request.getDescription(false),
                ex.getSku(),
                ex.getExistingGoodsId(),
                ex.getExistingGoodsName(),
                ex.getExistingGoodsCategory()
        );
        return new ResponseEntity<>(errorDetails, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGlobalException(Exception ex, WebRequest request) {
        ErrorDetails errorDetails = new ErrorDetails(new Date(), ex.getMessage(), request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}

// 错误详情类
class ErrorDetails {
    private Date timestamp;
    private String message;
    private String details;

    public ErrorDetails(Date timestamp, String message, String details) {
        this.timestamp = timestamp;
        this.message = message;
        this.details = details;
    }

    // Getters and setters
    public Date getTimestamp() { return timestamp; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}

// 扩展的错误详情类，用于包含额外信息
class GoodsErrorDetails extends ErrorDetails {
    private String sku;
    private Long existingGoodsId;
    private String existingGoodsName;
    private String existingGoodsCategory;

    public GoodsErrorDetails(Date timestamp, String message, String details,
                             String sku, Long existingGoodsId, String existingGoodsName, String existingGoodsCategory) {
        super(timestamp, message, details);
        this.sku = sku;
        this.existingGoodsId = existingGoodsId;
        this.existingGoodsName = existingGoodsName;
        this.existingGoodsCategory = existingGoodsCategory;
    }

    // Getters
    public String getSku() { return sku; }
    public Long getExistingGoodsId() { return existingGoodsId; }
    public String getExistingGoodsName() { return existingGoodsName; }
    public String getExistingGoodsCategory() { return existingGoodsCategory; }
}