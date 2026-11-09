package com.smartwms.inventory.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 库存查询出参（API-INV-001）。
 * availableQty = qty - lock_qty 由服务端计算；
 * batchStatus 返回批次状态（文档 11.6 待确认采纳「返回以便前端标识」：1正常/2冻结/3用尽）。
 */
@Data
public class InventoryItemVO {

    private Long id;
    private Long warehouseId;
    private String warehouseName;
    private Long areaId;
    private String areaName;
    private Long locationId;
    private String locationCode;
    private Long productId;
    private String productCode;
    private String productName;
    private Long categoryId;
    private String categoryName;
    private String unit;
    private Long batchId;
    private String batchNo;
    private LocalDate productionDate;
    private LocalDate expiryDate;
    /** 批次状态：1正常/2冻结/3用尽（待确认项采纳返回） */
    private Integer batchStatus;
    private BigDecimal qty;
    private BigDecimal lockQty;
    /** 可用库存 = qty - lock_qty */
    private BigDecimal availableQty;
    private Integer version;
    private LocalDateTime updateTime;
}
