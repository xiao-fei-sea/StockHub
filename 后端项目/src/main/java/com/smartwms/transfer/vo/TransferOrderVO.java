package com.smartwms.transfer.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 调拨单出参（列表/详情/动作响应）。
 * statusDesc 单据状态描述；confirmStatus 中文标签（未确认/已确认）；
 * actualQty 由明细 actual_qty 服务端汇总（transfer_order 主表无该列）；
 * items 仅详情与创建/修改响应携带，列表与其余动作为 null；
 * rejectReason 由 remark 中「[驳回] …」段解析（主表无 reject_reason 列，13.7 待确认采纳落 remark）。
 */
@Data
public class TransferOrderVO {

    private Long id;
    private String transferNo;
    private Long srcWarehouseId;
    private String srcWarehouseName;
    private Long destWarehouseId;
    private String destWarehouseName;
    private Integer status;
    private String statusDesc;
    /** 目标仓确认状态（中文） */
    private String confirmStatus;
    private BigDecimal totalQty;
    /** 实际调拨总数量（明细汇总，非表字段） */
    private BigDecimal actualQty;
    private String remark;
    /** 最近一次驳回原因（remark 解析） */
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
    private List<TransferItemVO> items;
}
