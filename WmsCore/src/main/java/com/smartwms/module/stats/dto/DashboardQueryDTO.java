package com.smartwms.module.stats.dto;

import lombok.Data;

/**
 * 首页看板汇总入参（API-STS-001）。
 * warehouseId 空 = 数据权限内全部仓库；越权返回 40302。
 */
@Data
public class DashboardQueryDTO {

    /** 仓库过滤（空 = 数据权限内全部仓库） */
    private Long warehouseId;
}
