package org.example.roadsimulation.exception;

/**
 * 货物正在使用中异常
 * 当尝试删除被引用的货物时抛出
 */
public class GoodsInUseException extends RuntimeException {

    public GoodsInUseException(String message) {
        super(message);
    }

    public GoodsInUseException(String message, Throwable cause) {
        super(message, cause);
    }

    public GoodsInUseException(Long goodsId, int usageCount) {
        super(String.format("无法删除货物(ID: %d)，该货物已被 %d 个运单项引用", goodsId, usageCount));
    }
}