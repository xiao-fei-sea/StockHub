package com.smartwms.module.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改商品分类入参（API-CAT-003）。
 * 禁止移动到自身或子孙（40901）；变更 parentId 时服务端级联重算 level。
 */
@Data
public class CategoryUpdateDTO {

    /** 上级分类 id，根为 0 */
    @NotNull(message = "parentId 不能为空")
    private Long parentId;

    @NotBlank(message = "categoryCode 不能为空")
    @Size(max = 50, message = "categoryCode 长度不能超过 50 个字符")
    private String categoryCode;

    @NotBlank(message = "categoryName 不能为空")
    @Size(max = 100, message = "categoryName 长度不能超过 100 个字符")
    private String categoryName;

    /** 排序号；null 表示不变 */
    private Integer sort;

    @Min(value = 0, message = "status 仅允许 0 或 1")
    @Max(value = 1, message = "status 仅允许 0 或 1")
    private Integer status;
}
