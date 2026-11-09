package com.smartwms.product.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 商品分页查询入参（API-PROD-001）。
 * categoryId 默认精确匹配（是否级联下级——待确认）；默认按 id 降序。
 */
@Data
public class ProductPageQueryDTO {

    @Min(value = 1, message = "pageNum 必须大于等于 1")
    private Integer pageNum = 1;

    @Min(value = 1, message = "pageSize 必须大于等于 1")
    private Integer pageSize = 20;

    /** 模糊查询：productCode / productName / brand */
    private String keyword;

    /** 分类 id（精确匹配） */
    private Long categoryId;

    /** 状态：0 停用 / 1 上架 */
    private Integer status;

    /** 是否保质期管理：0/1 */
    private Integer shelfLifeFlag;

    /** 是否批次管理：0/1 */
    private Integer batchFlag;
}
