package com.smartwms.module.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 新增部门入参（API-DEPT-002）。ancestors 由服务端按 parentId 生成，不接受前端传入。
 */
@Data
public class DeptCreateDTO {

    /** 父部门 id，根节点为 0 */
    @NotNull(message = "parentId 不能为空")
    private Long parentId;

    @NotBlank(message = "deptName 不能为空")
    @jakarta.validation.constraints.Size(max = 50, message = "deptName 长度不能超过 50 个字符")
    private String deptName;

    /** 排序号，默认 0 */
    private Integer sort = 0;

    /** 0 停用 / 1 正常，默认 1 */
    @Min(value = 0, message = "status 仅允许 0 或 1")
    @Max(value = 1, message = "status 仅允许 0 或 1")
    private Integer status = 1;
}
