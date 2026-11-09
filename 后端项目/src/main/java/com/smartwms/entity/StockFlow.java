package com.smartwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存变动流水表 stock_flow（期初 + Σ流水 = 当前库存，所有库存变动必须产生流水）。
 */
@Data
@TableName("stock_flow")
public class StockFlow {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 流水号（LS+yyyyMMdd+5位序号） */
    private String flowNo;
    private Long warehouseId;
    private Long productId;
    private Long batchId;
    private Long locationId;
    /** 变动方向：IN 入库 / OUT 出库 */
    private String direction;
    /** 本次变动数量 */
    private BigDecimal qty;
    private BigDecimal beforeQty;
    private BigDecimal afterQty;
    /** 流水类型：1入库/2出库/3调拨入/4调拨出/5盘盈/6盘亏/7调整 */
    private Integer flowType;
    /** 关联单据类型：INBOUND/OUTBOUND/TRANSFER/STOCKTAKE/ADJUST/INIT */
    private String refType;
    private Long refId;
    private String refNo;
    private Long operatorId;
    private LocalDateTime operTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;
    private Integer deleted;
}
