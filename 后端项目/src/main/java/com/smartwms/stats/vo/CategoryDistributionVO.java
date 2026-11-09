package com.smartwms.stats.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 库存分类分布出参（API-STS-003）。
 * 按 product.category_id 逐级上溯至指定 level 分组；未分类商品归入
 * categoryId=null 的「未分类」分组并置尾；qtyRatio = qty / totalQty（3 位小数）。
 */
@Data
@Builder
public class CategoryDistributionVO {

    /** 实际分组层级（= 请求 level） */
    private Integer groupLevel;
    private BigDecimal totalQty;
    /** 权限范围内有库存的 distinct product_id 数 */
    private Integer totalSku;
    private List<CategoryItem> items;

    /** 分类聚合项。 */
    @Data
    @Builder
    public static class CategoryItem {
        /** 未分类分组为 null */
        private Long categoryId;
        private String categoryCode;
        private String categoryName;
        private Integer skuCount;
        private BigDecimal qty;
        /** qty / totalQty，3 位小数 */
        private BigDecimal qtyRatio;
    }
}
