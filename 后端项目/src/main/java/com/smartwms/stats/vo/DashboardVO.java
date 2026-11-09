package com.smartwms.stats.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 首页看板汇总出参（API-STS-001）。
 * 统计口径：库存取 inventory 当前值；今日出入库量按 stock_flow.oper_time、
 * 单据数按 status=3 的 post_time；待审数按各单据表 status=1；
 * 占用率 = 启用货位中 current_status=占用 占比（2 位小数）。无数据时各项为 0。
 */
@Data
public class DashboardVO {

    /** 统计日期 yyyy-MM-dd */
    private String statDate;
    /** 统计仓库范围描述 */
    private String warehouseScope;

    private InventoryStats inventory = new InventoryStats();
    private TodayStats today = new TodayStats();
    private PendingStats pending = new PendingStats();
    private AlertStats alert = new AlertStats();
    private WarehouseStats warehouse = new WarehouseStats();

    /** 当前库存概况。 */
    @Data
    public static class InventoryStats {
        /** Σ inventory.qty */
        private BigDecimal totalQty = BigDecimal.ZERO;
        /** distinct product_id（qty > 0） */
        private Integer skuCount = 0;
        /** distinct batch_id（qty > 0） */
        private Integer batchCount = 0;
        /** 有库存货位数（distinct location_id，qty > 0） */
        private Integer locationOccupiedCount = 0;
    }

    /** 今日出入库（流水 oper_time 口径 + 单据 post_time 口径）。 */
    @Data
    public static class TodayStats {
        /** 今日 status=3 入库单数 */
        private Integer inboundOrderCount = 0;
        /** 今日 stock_flow direction=IN 的 Σ qty */
        private BigDecimal inboundQty = BigDecimal.ZERO;
        /** 今日 status=3 出库单数 */
        private Integer outboundOrderCount = 0;
        /** 今日 stock_flow direction=OUT 的 Σ qty */
        private BigDecimal outboundQty = BigDecimal.ZERO;
    }

    /** 待审核单据数（各单据表 status=1）。 */
    @Data
    public static class PendingStats {
        private Integer inboundPendingCount = 0;
        private Integer outboundPendingCount = 0;
        private Integer stocktakePendingCount = 0;
        private Integer transferPendingCount = 0;
    }

    /** 预警概况（stock_alert_record）。 */
    @Data
    public static class AlertStats {
        /** status=未处理 数量 */
        private Integer unhandledCount = 0;
        /** 未处理且 level=严重 数量 */
        private Integer seriousCount = 0;
    }

    /** 仓库与货位概况。 */
    @Data
    public static class WarehouseStats {
        /** 数据权限内仓库数 */
        private Integer warehouseCount = 0;
        /** 启用（status=1）货位总数 */
        private Integer locationTotal = 0;
        /** current_status=占用 货位数 */
        private Integer locationUsed = 0;
        /** locationUsed / locationTotal，2 位小数 */
        private BigDecimal occupancyRate = BigDecimal.ZERO;
    }
}
