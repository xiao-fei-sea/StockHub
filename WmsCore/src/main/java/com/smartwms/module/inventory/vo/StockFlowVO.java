package com.smartwms.module.inventory.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存流水出参（入库过账响应 data.flows；出库/盘点/调拨复用）。
 */
@Data
@Builder
public class StockFlowVO {

    private String flowNo;
    /** 发生仓库（调拨等跨仓流水用于区分源/目标仓，接口文档示例包含该字段） */
    private Long warehouseId;
    private Long productId;
    private Long batchId;
    private Long locationId;
    /** 变动方向：IN / OUT */
    private String direction;
    private BigDecimal qty;
    private BigDecimal beforeQty;
    private BigDecimal afterQty;
    private Integer flowType;
    private String refType;
    private Long refId;
    private String refNo;
    private LocalDateTime operTime;
}
