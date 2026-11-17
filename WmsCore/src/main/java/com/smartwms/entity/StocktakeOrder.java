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
 * 盘点单主表 stocktake_order。
 * 单据不做逻辑删除（deleted 恒 0）；version 乐观锁；
 * scope：1按库区/2按仓库；diff_status：0未处理/1已处理（过账置已处理）。
 * 主表无 reject_reason 列（待确认项采纳「驳回原因落 remark」）。
 */
@Data
@TableName("stocktake_order")
public class StocktakeOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 盘点单号（PD+yyyyMMdd+3位序号，唯一） */
    private String stocktakeNo;
    private Long warehouseId;
    /** 盘点库区（全仓盘点为 null） */
    private Long areaId;
    /** 单据状态：0草稿/1待审核/2已审核/3已完成/4已驳回/5已取消 */
    private Integer status;
    /** 差异处理状态：0未处理/1已处理 */
    private Integer diffStatus;
    /** 盘点范围：1按库区/2按仓库 */
    private Integer scope;
    private Long auditorId;
    private LocalDateTime auditTime;
    private Long posterId;
    private LocalDateTime postTime;
    /** 差异总数量（Σ differ_qty，盘盈为正盘亏为负） */
    private BigDecimal totalDifferQty;
    private LocalDate planDate;
    private LocalDateTime finishTime;
    private String remark;

    @Version
    private Integer version;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;
    private Integer deleted;
}
