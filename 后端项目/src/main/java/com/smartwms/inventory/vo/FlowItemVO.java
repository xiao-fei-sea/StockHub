package com.smartwms.inventory.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存变动流水出参（API-INV-002）。
 * flowType 返回中文标签（入库/出库/调拨入/调拨出/盘盈/盘亏/调整）；
 * refType 返回中文单据类型（入库单/出库单/调拨单/盘点单/调整单/期初）。
 */
@Data
public class FlowItemVO {

    private Long id;
    private String flowNo;
    private Long warehouseId;
    private String warehouseName;
    private Long productId;
    private String productCode;
    private String productName;
    private Long batchId;
    private String batchNo;
    private Long locationId;
    private String locationCode;
    /** 变动方向：IN / OUT */
    private String direction;
    private BigDecimal qty;
    private BigDecimal beforeQty;
    private BigDecimal afterQty;
    /** 流水类型（中文标签） */
    private String flowType;
    /** 关联单据类型（中文标签） */
    private String refType;
    private Long refId;
    private String refNo;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime operTime;
    private LocalDateTime createTime;
}
