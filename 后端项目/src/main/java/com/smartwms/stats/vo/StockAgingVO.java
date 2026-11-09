package com.smartwms.stats.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 库存账龄/滞销出参（API-STS-006）。
 * 批次账龄 = statTime - 该批次最近一次 IN 流水 oper_time（缺则批次创建时间）；
 * 滞销按（仓库, 商品）汇总，排序 daysSinceLastOutbound 降序、无出库记录（null）排最前。
 */
@Data
@Builder
public class StockAgingVO {

    private LocalDateTime statTime;
    private List<AgingSegment> agingSegments;
    private List<SlowMovingItem> slowMoving;

    /** 账龄分段。 */
    @Data
    @Builder
    public static class AgingSegment {
        /** 如 "0-30 天"，末段 "180 天以上" */
        private String segment;
        private Integer minDays;
        /** 末段为 null（无上限） */
        private Integer maxDays;
        private BigDecimal qty;
        private Integer skuCount;
        /** 该段 qty / 全部分段合计，3 位小数 */
        private BigDecimal qtyRatio;
    }

    /** 滞销清单项。 */
    @Data
    @Builder
    public static class SlowMovingItem {
        private Long productId;
        private String productCode;
        private String productName;
        private Long warehouseId;
        private String warehouseName;
        private BigDecimal qty;
        /** 最近一次入库时间（流水 IN，缺则批次创建时间） */
        private LocalDateTime lastInboundTime;
        /** statTime - lastInboundTime 天数 */
        private Integer agingDays;
        /** 最近一次出库时间，无出库记录为 null */
        private LocalDateTime lastOutboundTime;
        /** statTime - lastOutboundTime 天数，无出库记录为 null */
        private Integer daysSinceLastOutbound;
    }
}
