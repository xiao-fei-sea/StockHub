package com.smartwms.module.stats.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 仓库货位利用率入参（API-STS-004）。
 * groupBy ∈ {warehouse(默认), area}；area 维度须指定 warehouseId（否则 40001）；
 * 利用率分母固定为 status=1（启用）货位数。
 */
@Data
public class OccupancyQueryDTO {

    /** 仓库过滤（空 = 数据权限内全部仓库） */
    private Long warehouseId;

    /** 分组维度：warehouse / area */
    @Pattern(regexp = "warehouse|area", message = "groupBy 仅支持 warehouse/area")
    private String groupBy = "warehouse";
}
