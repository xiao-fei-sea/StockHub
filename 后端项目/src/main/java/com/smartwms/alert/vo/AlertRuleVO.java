package com.smartwms.alert.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预警规则出参（列表/详情/动作响应）。
 * warehouseId 为 null 时 warehouseName 显示「全部仓库」；
 * alertType 英文标签 + alertTypeDesc 中文描述；statusDesc 启用/停用；
 * categoryName/productName 关联反查，查不到置 null。
 */
@Data
public class AlertRuleVO {

    private Long id;
    private String ruleName;
    private Long warehouseId;
    /** 仓库名；warehouseId 为空显示「全部仓库」 */
    private String warehouseName;
    private Long categoryId;
    private String categoryName;
    private Long productId;
    private String productName;
    /** 预警类型（英文） */
    private String alertType;
    /** 预警类型（中文描述） */
    private String alertTypeDesc;
    private BigDecimal thresholdValue;
    private Integer nearDays;
    private BigDecimal minQty;
    private BigDecimal maxQty;
    private Integer status;
    private String statusDesc;
    private String remark;
    private String createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
