package com.smartwms.stats.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存账龄/滞销入参（API-STS-006）。
 * agingDays 逗号分隔升序正整数、最多 6 段（格式非法/段数超限返回 40001，服务层解析校验）；
 * topN ∈ [1,100] 默认 20；minStockQty 默认 0；统计仅含 qty > 0 记录。
 * 滞销阈值复用账龄最大分段（15.6 待确认采纳文档默认）。
 */
@Data
public class StockAgingQueryDTO {

    /** 仓库过滤（空 = 数据权限内全部仓库） */
    private Long warehouseId;

    /** 账龄分段天数（逗号分隔，默认 "30,60,90,180"，最多 6 段升序正整数） */
    @NotBlank(message = "agingDays 不能为空")
    private String agingDays = "30,60,90,180";

    /** 滞销清单条数（默认 20，取值 1~100） */
    @Min(value = 1, message = "topN 取值范围 1~100")
    @Max(value = 100, message = "topN 取值范围 1~100")
    private Integer topN = 20;

    /** 滞销判定最小库存量（默认 0，仅统计 qty > 0 记录） */
    @PositiveOrZero(message = "minStockQty 不能为负数")
    private BigDecimal minStockQty = BigDecimal.ZERO;
}
