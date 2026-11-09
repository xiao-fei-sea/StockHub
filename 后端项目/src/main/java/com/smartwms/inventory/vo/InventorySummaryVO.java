package com.smartwms.inventory.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 库存汇总出参（API-INV-003 看板）。
 * 文档 11.6 待确认项口径：
 * 1. 采纳「固定同时返回」——items 按 groupBy 维度（默认 warehouse）聚合，
 *    byCategory 固定按商品一级分类聚合（待确认项2采纳一级分类口径）；
 * 2. 汇总口径：Σ inventory.qty，SKU 数为 distinct product_id。
 */
@Data
public class InventorySummaryVO {

    /** 本次 items 的聚合维度：warehouse / category / product */
    private String groupType;
    /** 权限范围内库存总量 */
    private BigDecimal totalQty;
    /** 权限范围内 SKU 数（distinct product_id） */
    private Long totalSku;
    /** 按 groupBy 维度聚合的汇总数组 */
    private List<SummaryItem> items;
    /** 固定按一级分类聚合的汇总数组 */
    private List<SummaryItem> byCategory;

    /** 汇总项（按维度取对应字段：仓库/分类/商品，未用字段为 null）。 */
    @Data
    public static class SummaryItem {
        private Long warehouseId;
        private String warehouseName;
        private Long categoryId;
        private String categoryName;
        private Long productId;
        private String productCode;
        private String productName;
        /** SKU 数（product 维度恒为 1） */
        private Long skuCount;
        private BigDecimal qty;
    }
}
