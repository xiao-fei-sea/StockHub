package com.smartwms.alert.dto;

import lombok.Data;

/**
 * 预警规则分页查询入参（API-ALR-001）。
 * alertType 用英文标签（LOW_STOCK 等，非法 40001）；status 0停用/1启用；
 * 数据权限过滤：warehouse_id 非空按权限过滤，warehouse_id 为空（全部仓库）
 * 的规则仅 admin/数据分析员（不受限角色）可见与管理。
 */
@Data
public class AlertRulePageQueryDTO {

    private Integer pageNum = 1;

    private Integer pageSize = 20;

    /** 规则名称模糊匹配 */
    private String keyword;

    /** 仓库 ID（越权 40302） */
    private Long warehouseId;

    /** 预警类型（英文标签）：LOW_STOCK/HIGH_STOCK/NEAR_EXPIRY/EXPIRED */
    private String alertType;

    /** 规则状态：0停用/1启用 */
    private Integer status;
}
