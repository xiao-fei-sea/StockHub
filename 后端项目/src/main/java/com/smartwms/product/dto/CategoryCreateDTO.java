package com.smartwms.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增商品分类入参（API-CAT-002）。
 * categoryCode 全局唯一（40904），建议 FL- 前缀；level 由服务端按 parentId 自动生成。
 */
@Data
public class CategoryCreateDTO {

    /** 上级分类 id，根分类传 0 */
    @NotNull(message = "parentId 不能为空")
    private Long parentId;

    @NotBlank(message = "categoryCode 不能为空")
    @Size(max = 50, message = "categoryCode 长度不能超过 50 个字符")
    private String categoryCode;

    @NotBlank(message = "categoryName 不能为空")
    @Size(max = 100, message = "categoryName 长度不能超过 100 个字符")
    private String categoryName;

    /** 排序号，默认 0（同级按 sort/id 升序） */
    private Integer sort = 0;

    @Min(value = 0, message = "status 仅允许 0 或 1")
    @Max(value = 1, message = "status 仅允许 0 或 1")
    private Integer status = 1;
}
