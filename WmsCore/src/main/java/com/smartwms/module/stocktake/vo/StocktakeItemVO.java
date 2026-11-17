package com.smartwms.module.stocktake.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 盘点明细出参（API-STK-003 详情）。
 * status 返回中文（差异已确认/无需处理）；反查货位/商品/批次编码与单位。
 */
@Data
public class StocktakeItemVO {

    private Long id;
    private Integer lineNo;
    private Long locationId;
    private String locationCode;
    private Long productId;
    private String productCode;
    private String productName;
    private Long batchId;
    private String batchNo;
    /** 账面数（创建时点快照） */
    private BigDecimal bookQty;
    /** 实盘数（null 表示未盘） */
    private BigDecimal actualQty;
    /** 差异数 = actual - book */
    private BigDecimal differQty;
    private String diffReason;
    /** 明细差异状态（中文） */
    private String status;
    private String unit;
}
