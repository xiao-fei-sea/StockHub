package com.smartwms.stats.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 出入库 Top 商品出参（API-STS-005）。
 * qty 按 type 取 IN/OUT/合计（区间内 stock_flow，oper_time 口径，全部流水类型）；
 * orderCount = 区间内涉及该商品的已过账单据数（refType=INBOUND/OUTBOUND 去重，流水仅在过账时产生）；
 * 按 qty 降序 rank 从 1 起始。
 */
@Data
@Builder
public class TopProductsVO {

    /** inbound / outbound / total */
    private String type;
    private String startDate;
    private String endDate;
    private List<TopItem> items;

    /** Top 商品项。 */
    @Data
    @Builder
    public static class TopItem {
        private Integer rank;
        private Long productId;
        private String productCode;
        private String productName;
        /** 商品自身分类名（无分类为 null） */
        private String categoryName;
        /** 排序依据数量（按 type 口径） */
        private BigDecimal qty;
        /** 区间内已过账单据数（按 type 对应单据表去重） */
        private Integer orderCount;
        private BigDecimal inboundQty;
        private BigDecimal outboundQty;
    }
}
