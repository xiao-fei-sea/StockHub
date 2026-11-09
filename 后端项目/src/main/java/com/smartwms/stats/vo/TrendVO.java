package com.smartwms.stats.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 出入库趋势出参（API-STS-002）。
 * 序列按日期升序、长度 = days、无数据日补 0；
 * 数量按 stock_flow.oper_time（全部 IN/OUT 流水，含调拨与盘盈亏）、
 * 单量按 status=3 单据 post_time。
 */
@Data
@Builder
public class TrendVO {

    private Integer days;
    /** 起始日期 yyyy-MM-dd */
    private String startDate;
    /** 结束日期 yyyy-MM-dd */
    private String endDate;
    private List<TrendPoint> series;
    private Summary summary;

    /** 单日趋势点。 */
    @Data
    @Builder
    public static class TrendPoint {
        private String date;
        private BigDecimal inboundQty;
        private BigDecimal outboundQty;
        private Integer inboundCount;
        private Integer outboundCount;
    }

    /** 区间合计。 */
    @Data
    @Builder
    public static class Summary {
        private BigDecimal inboundQtyTotal;
        private BigDecimal outboundQtyTotal;
    }
}
