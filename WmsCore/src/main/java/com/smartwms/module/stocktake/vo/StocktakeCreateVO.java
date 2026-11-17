package com.smartwms.module.stocktake.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 创建盘点单响应出参（API-STK-002 响应示例结构，含 itemCount/bookQtyTotal 汇总）。
 */
@Data
@Builder
public class StocktakeCreateVO {

    private Long id;
    private String stocktakeNo;
    private Integer status;
    private String statusDesc;
    /** 盘点范围（中文） */
    private String scope;
    private Long warehouseId;
    private Long areaId;
    /** 自动生成的明细行数 */
    private Long itemCount;
    /** 账面数合计（账面快照汇总） */
    private java.math.BigDecimal bookQtyTotal;
    private LocalDate planDate;
    private Integer version;
    private LocalDateTime createTime;
}
