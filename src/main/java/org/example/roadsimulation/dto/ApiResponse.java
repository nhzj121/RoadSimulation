package org.example.roadsimulation.dto;

/**
 * 通用接口响应对象
 *
 * 作用：
 * 1. 统一后端返回给前端的数据格式
 * 2. 成功和失败都用同一种结构返回
 *
 * 泛型 T 表示真正的业务数据类型，例如：
 * - ApiResponse<RouteResponseDTO>
 * - ApiResponse<GaodeRouteResponse>
 * - ApiResponse<List<RouteResponseDTO>>
 */
public class ApiResponse<T> {

    /**
     * 是否成功
     * true = 成功
     * false = 失败
     */
    private boolean success;

    /**
     * 返回消息
     * 例如：
     * - 成功
     * - 查询成功
     * - 路线规划失败
     */
    private String message;

    /**
     * 实际返回的数据
     */
    private T data;

    /**
     * 成功响应：只传业务数据，消息默认写“成功”
     *
     * 例如：
     * ApiResponse.success(routeDto)
     */
    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setMessage("成功");
        response.setData(data);
        return response;
    }

    /**
     * 成功响应：自定义成功消息 + 业务数据
     *
     * 例如：
     * ApiResponse.success("路线规划成功", responseData)
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setMessage(message);
        response.setData(data);
        return response;
    }

    /**
     * 失败响应：只传失败消息
     *
     * 例如：
     * ApiResponse.error("参数错误")
     */
    public static <T> ApiResponse<T> error(String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setMessage(message);
        response.setData(null);
        return response;
    }

    /**
     * getter / setter
     */
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
