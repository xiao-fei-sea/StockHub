package com.smartwms.alert.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 手动触发扫描入参（API-ALR-008，可为空对象）。
 * warehouseId 限定扫描仓库（越权 40302，空 = 数据权限内全部）；
 * ruleIds 限定规则（空 = 全部启用规则，≤100 个）。
 */
@Data
public class AlertScanDTO {

    /** 仅扫描指定仓库，空 = 全部（数据权限内） */
    private Long warehouseId;

    /** 仅扫描指定规则，空 = 全部启用规则 */
    @Size(max = 100, message = "ruleIds 最多 100 个")
    private List<Long> ruleIds;
}
