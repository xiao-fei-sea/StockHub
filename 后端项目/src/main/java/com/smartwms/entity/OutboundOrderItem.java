package com.smartwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 出库单明细表 outbound_order_item（明细不走乐观锁，整体先删后插）。
 * batch_no 创建时可空（过账 FIFO 分配）；batch_id/batch_no/location_id 过账时回填。
 * 与入库明细不同：无 production_date/expiry_date（效期信息在批次表，FIFO 排序直接用批次字段）。
 */
@Data
@TableName("outbound_order_item")
public class OutboundOrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;
    private Integer lineNo;
    private Long productId;
    /** 指定批次号（可空，过账 FIFO 分配后回填） */
    private String batchNo;
    /** 分配到的库存批次 ID（过账时回填） */
    private Long batchId;
    /** 出货货位（过账时按分配结果回填主批次货位） */
    private Long locationId;
    private BigDecimal planQty;
    private BigDecimal actualQty;
    /** 单位（冗余自商品表） */
    private String unit;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;
    private Integer deleted;
}
