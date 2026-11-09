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
 * 出库单主表 outbound_order。
 * 单据不做逻辑删除（deleted 恒 0，删除接口物理删除草稿）；
 * version 为乐观锁（@Version 由乐观锁插件维护，更新时自动 +1）。
 * 注意：主表无 actual_qty 列（以数据库脚本为准），实发总数由明细汇总。
 */
@Data
@TableName("outbound_order")
public class OutboundOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 出库单号（CK+yyyyMMdd+3位序号，唯一） */
    private String outboundNo;
    /** 出库类型：1销售出库/2调拨出库/3退货出库/4报损出库 */
    private Integer orderType;
    private Long customerId;
    private String sourceOrderNo;
    private LocalDate expectDate;
    /** 单据状态：0草稿/1待审核/2已审核/3已完成/4已驳回/5已取消 */
    private Integer status;
    private Long auditorId;
    private LocalDateTime auditTime;
    private Long posterId;
    private LocalDateTime postTime;
    private Long warehouseId;
    /** 计划总数量（明细 planQty 汇总，服务端维护） */
    private BigDecimal totalQty;
    /** 拣货人 ID（可空） */
    private Long pickUserId;
    private String remark;

    @Version
    private Integer version;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;
    private Integer deleted;
}
