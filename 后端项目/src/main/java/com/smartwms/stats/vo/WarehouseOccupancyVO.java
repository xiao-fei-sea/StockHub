package com.smartwms.stats.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 仓库货位利用率出参（API-STS-004）。
 * 分母固定为 status=1（启用）货位数；利用率 2 位小数；
 * area 维度时 items 含 areaId/areaName（warehouseId/warehouseName 同时给出）。
 */
@Data
@Builder
public class WarehouseOccupancyVO {

    /** warehouse / area */
    private String groupType;
    private Summary summary;
    private List<OccItem> items;

    /** 全量汇总。 */
    @Data
    @Builder
    public static class Summary {
        private Integer locationTotal;
        private Integer locationUsed;
        private Integer locationLocked;
        private Integer locationDisabled;
        private Integer locationIdle;
        private BigDecimal occupancyRate;
    }

    /** 分组货位与库存概况。 */
    @Data
    @Builder
    public static class OccItem {
        private Long warehouseId;
        private String warehouseName;
        /** area 维度时非空 */
        private Long areaId;
        private String areaName;
        private Integer locationTotal;
        private Integer locationUsed;
        private Integer locationIdle;
        private Integer locationLocked;
        private Integer locationDisabled;
        private BigDecimal occupancyRate;
        /** 分组内 distinct product_id（inventory） */
        private Integer skuCount;
        /** 分组内 Σ inventory.qty */
        private BigDecimal qty;
    }
}
