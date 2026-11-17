package com.smartwms.module.stats.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 库存分类分布入参（API-STS-003）。
 * level 默认 1；超过分类最大层级返回 40001；
 * 未分类商品（category_id 为空）归入「未分类」分组（15.6 待确认采纳文档默认保留）。
 */
@Data
public class CategoryDistributionQueryDTO {

    /** 仓库过滤（空 = 数据权限内全部仓库） */
    private Long warehouseId;

    /** 分组分类层级（默认 1，须 ∈ [1, 分类最大层级]） */
    @Min(value = 1, message = "level 须为正整数")
    private Integer level = 1;
}
