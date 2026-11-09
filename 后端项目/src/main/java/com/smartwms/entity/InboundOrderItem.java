package com.smartwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 入库单明细表 inbound_order_item（明细不走乐观锁，整体先删后插）。 */
@Data
@TableName("inbound_order_item")
public class InboundOrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;
    private Integer lineNo;
    private Long productId;
    /** 目标货位（过账时写入该货位库存） */
    private Long locationId;
    /** 批次号（batch_flag=0 商品为 DEFAULT） */
    private String batchNo;
    private LocalDate productionDate;
    private LocalDate expiryDate;
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
