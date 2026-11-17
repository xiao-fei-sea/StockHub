package com.smartwms.module.inbound.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 入库单出参（列表/详情/动作响应）。
 * orderType 返回中文枚举名（OpenAPI 口径）；statusDesc 单据状态描述；
 * items 仅详情与创建/修改响应携带，列表与其余动作为 null；
 * 驳回原因存于 remark（inbound_order 无 reject_reason 字段，文档待确认项采纳「落 remark」）。
 */
@Data
public class InboundOrderVO {

    private Long id;
    private String inboundNo;
    private String orderType;
    private Long supplierId;
    private String supplierName;
    private String sourceOrderNo;
    private LocalDate expectDate;
    private Integer status;
    private String statusDesc;
    private Long warehouseId;
    private String warehouseName;
    private BigDecimal totalQty;
    private BigDecimal actualQty;
    private String remark;
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
    private List<InboundItemVO> items;
}
