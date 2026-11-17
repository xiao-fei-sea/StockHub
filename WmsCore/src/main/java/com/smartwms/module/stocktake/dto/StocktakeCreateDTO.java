package com.smartwms.module.stocktake.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 创建盘点单入参（API-STK-002）。
 * scope 中文（按库区/按仓库，按库区时 areaId 必填 40001）；
 * filterProductIds/filterCategoryIds 可选限定范围；includeEmptyLocation 默认 false。
 * 注意：stocktake_order_item.product_id 非空，空货位（无商品）零账面行无法落库
 * ——12.7 待确认项采纳「默认不生成」，该参数暂不产生空货位行。
 */
@Data
public class StocktakeCreateDTO {

    @NotNull(message = "warehouseId 不能为空")
    private Long warehouseId;

    @NotBlank(message = "scope 不能为空")
    private String scope;

    /** scope=按库区 必填且须属于 warehouseId；scope=按仓库 忽略 */
    private Long areaId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate planDate;

    @Size(max = 500, message = "remark 长度不能超过 500 个字符")
    private String remark;

    /** 仅盘点指定商品（可选） */
    private List<Long> filterProductIds;

    /** 仅盘点指定分类下商品（可选，含子分类） */
    private List<Long> filterCategoryIds;

    /** 是否包含空货位，默认 false（暂不生成空货位行，见类注释） */
    private Boolean includeEmptyLocation;
}
