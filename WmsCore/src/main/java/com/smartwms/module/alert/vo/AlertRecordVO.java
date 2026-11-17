package com.smartwms.module.alert.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预警记录出参（列表响应）。
 * ruleName/warehouseName/productCode/productName/handleUserName 关联反查，查不到置 null；
 * alertType 英文标签 + alertTypeDesc 中文；level/status 中文标签（提示/警告/严重、未处理/已处理）；
 * currentValue/thresholdValue 为扫描时快照。
 */
@Data
public class AlertRecordVO {

    private Long id;
    private Long ruleId;
    private String ruleName;
    /** 预警类型（英文） */
    private String alertType;
    /** 预警类型（中文描述） */
    private String alertTypeDesc;
    private Long warehouseId;
    private String warehouseName;
    private Long productId;
    private String productCode;
    private String productName;
    private Long batchId;
    /** 当前值（当前库存量 / 距过期天数） */
    private BigDecimal currentValue;
    /** 阈值快照 */
    private BigDecimal thresholdValue;
    /** 级别（中文） */
    private String level;
    /** 处理状态（中文） */
    private String status;
    private String handleRemark;
    private Long handleUser;
    private String handleUserName;
    private LocalDateTime handleTime;
    private LocalDateTime createTime;
}
