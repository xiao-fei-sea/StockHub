package com.smartwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 盘点单明细表 stocktake_order_item（无 version，明细不走乐观锁）。
 * book_qty 为创建时点快照（12.7 待确认采纳「创建时快照」口径）；
 * status：0差异已确认/1无需处理；actual_qty 可空表示未盘。
 */
@Data
@TableName("stocktake_order_item")
public class StocktakeOrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;
    private Integer lineNo;
    private Long locationId;
    private Long productId;
    private Long batchId;
    /** 账面数（创建时从 inventory 快照） */
    private BigDecimal bookQty;
    /** 实盘数（录入，null 表示未盘） */
    private BigDecimal actualQty;
    /** 差异数 = actual_qty - book_qty（服务端计算） */
    private BigDecimal differQty;
    private String diffReason;
    /** 明细差异状态：0差异已确认/1无需处理 */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;
    private Integer deleted;
}
