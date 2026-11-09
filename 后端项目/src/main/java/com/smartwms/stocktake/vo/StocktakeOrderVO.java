package com.smartwms.stocktake.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 盘点单出参（列表/详情/动作响应）。
 * scope/diffStatus/rejectReason 均为中文展示：scope 按库区/按仓库、diffStatus 未处理/已处理；
 * rejectReason 由 remark 中「[驳回] …」段解析（主表无 reject_reason 列，待确认采纳落 remark）。
 * items 仅详情与实盘录入响应携带。
 */
@Data
public class StocktakeOrderVO {

    private Long id;
    private String stocktakeNo;
    private Long warehouseId;
    private String warehouseName;
    private Long areaId;
    private String areaName;
    /** 盘点范围（中文：按库区/按仓库） */
    private String scope;
    private Integer status;
    private String statusDesc;
    /** 差异处理状态（中文：未处理/已处理） */
    private String diffStatus;
    private LocalDate planDate;
    private BigDecimal totalDifferQty;
    private LocalDateTime finishTime;
    private String remark;
    /** 驳回原因（自 remark 解析，非空表示曾被驳回） */
    private String rejectReason;
    private Integer version;
    private Long auditorId;
    private String auditorName;
    private LocalDateTime auditTime;
    private Long posterId;
    private String posterName;
    private LocalDateTime postTime;
    private String createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<StocktakeItemVO> items;
}
