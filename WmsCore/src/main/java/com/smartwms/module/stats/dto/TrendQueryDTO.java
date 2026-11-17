package com.smartwms.module.stats.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 出入库趋势入参（API-STS-002）。
 * days ∈ [1,365]，缺省 30；warehouseId 空 = 数据权限内全部仓库。
 */
@Data
public class TrendQueryDTO {

    /** 统计天数（默认 30，取值 1~365，超出返回 40001） */
    @Min(value = 1, message = "days 取值范围 1~365")
    @Max(value = 365, message = "days 取值范围 1~365")
    private Integer days = 30;

    /** 仓库过滤（空 = 数据权限内全部仓库） */
    private Long warehouseId;
}
