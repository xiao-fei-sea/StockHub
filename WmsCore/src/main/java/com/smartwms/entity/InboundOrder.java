package com.smartwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 入库单主表 inbound_order。
 * 单据不做逻辑删除（deleted 恒 0，删除接口物理删除草稿）；
 * version 为乐观锁（@Version 由乐观锁插件维护，更新时自动 +1）。
 */
@Data
@TableName("inbound_order")
public class InboundOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 入库单号（RK+yyyyMMdd+3位序号，唯一） */
    private String inboundNo;
    /** 入库类型：1采购入库/2调拨入库/3退货入库/4盘盈入库 */
    private Integer orderType;
    private Long supplierId;
    private String sourceOrderNo;
    private LocalDate expectDate;
    /** 单据状态：0草稿/1待审核/2已审核/3已完成/4已驳回/5已取消 */
    private Integer status;
    private Long auditorId;
    private LocalDateTime auditTime;
    private Long posterId;
    private LocalDateTime postTime;
    /** 计划总数量（明细 planQty 汇总，服务端维护） */
    private BigDecimal totalQty;
    /** 实收总数量（明细 actualQty 汇总，服务端维护） */
    private BigDecimal actualQty;
    private Long warehouseId;
    private String remark;

    @Version
    private Integer version;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;
    private Integer deleted;
}
