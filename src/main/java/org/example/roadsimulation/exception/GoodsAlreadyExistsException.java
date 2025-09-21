package org.example.roadsimulation.exception;

/**
 * 货物已存在异常
 * 当尝试创建已存在的货物时抛出
 * 继承 RuntimeException 以保持与现有异常体系一致
 */
public class GoodsAlreadyExistsException extends RuntimeException {
    private final String sku;
    private final Long existingGoodsId;
    private final String existingGoodsName;
    private final String existingGoodsCategory;

    public GoodsAlreadyExistsException(String message, String sku, Long existingGoodsId,
                                       String existingGoodsName, String existingGoodsCategory) {
        super(message);
        this.sku = sku;
        this.existingGoodsId = existingGoodsId;
        this.existingGoodsName = existingGoodsName;
        this.existingGoodsCategory = existingGoodsCategory;
    }

    public GoodsAlreadyExistsException(String message, String sku, Long existingGoodsId,
                                       String existingGoodsName, String existingGoodsCategory, Throwable cause) {
        super(message, cause);
        this.sku = sku;
        this.existingGoodsId = existingGoodsId;
        this.existingGoodsName = existingGoodsName;
        this.existingGoodsCategory = existingGoodsCategory;
    }

    // Getter方法
    public String getSku() {
        return sku;
    }

    public Long getExistingGoodsId() {
        return existingGoodsId;
    }

    public String getExistingGoodsName() {
        return existingGoodsName;
    }

    public String getExistingGoodsCategory() {
        return existingGoodsCategory;
    }

    /**
     * 获取详细的错误信息，可用于日志记录
     */
    public String getDetailedMessage() {
        return String.format("%s. 已存在货物: ID=%d, 名称='%s', 分类='%s', SKU='%s'",
                getMessage(), existingGoodsId, existingGoodsName,
                existingGoodsCategory != null ? existingGoodsCategory : "无", sku);
    }
}