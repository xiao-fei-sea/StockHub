package com.smartwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 调拨单明细表 transfer_order_item（明细不走乐观锁，整体先删后插）。
 * batch_id/src_location_id 创建时可指定（不指定过账 FIFO 分配后回填）；
 * dest_location_id 创建时必填（13.7 待确认采纳「建议必填」）。
 * 无 unit 列（以数据库脚本为准），单位由商品表反查展示。
 */
@Data
@TableName("transfer_order_item")
public class TransferOrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;
    private Integer lineNo;
    private Long productId;
    /** 指定/分配到的源仓批次 ID（调拨沿用源仓批次，过账回填） */
    private Long batchId;
    /** 源货位（可空，过账按分配批次所在货位回填） */
    private Long srcLocationId;
    /** 目标货位（必填，须属于目标仓且状态可用） */
    private Long destLocationId;
    private BigDecimal planQty;
    private BigDecimal actualQty;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;
    private Integer deleted;
}
