package org.example.roadsimulation.dto;

public class BatchError {
    private int index;
    private String message;

    // 无参构造函数（JSON反序列化需要）
    public BatchError() {}

    public BatchError(int index, String message) {
        this.index = index;
        this.message = message;
    }

    public int getIndex() {
        return index;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "BatchError{" +
                "index=" + index +
                ", message='" + message + '\'' +
                '}';
    }
}
