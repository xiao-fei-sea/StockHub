package com.smartwms.alert.dto;

import lombok.Data;

/**
 * 预警记录分页查询入参（API-ALR-006）。
 * alertType 英文标签；status/level 中文标签（未处理/已处理、提示/警告/严重），非法 40001；
 * 按 warehouse_id 数据权限过滤（记录恒有仓库）。
 */
@Data
public class AlertRecordPageQueryDTO {

    private Integer pageNum = 1;

    private Integer pageSize = 20;

    /** 预警类型（英文标签）：LOW_STOCK/HIGH_STOCK/NEAR_EXPIRY/EXPIRED */
    private String alertType;

    /** 处理状态（中文）：未处理/已处理 */
    private String status;

    /** 仓库 ID（越权 40302） */
    private Long warehouseId;

    /** 预警级别（中文）：提示/警告/严重 */
    private String level;

    private Long productId;

    /** 生成开始日期 yyyy-MM-dd（含当天） */
    private String startDate;

    /** 生成结束日期 yyyy-MM-dd（含当天） */
    private String endDate;
}
