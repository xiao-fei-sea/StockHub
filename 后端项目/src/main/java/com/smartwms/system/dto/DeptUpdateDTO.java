package com.smartwms.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 修改部门入参（API-DEPT-003）：调父级时服务端同步重建本节点及全部后代 ancestors。
 */
@Data
public class DeptUpdateDTO {

    /** 新父部门 id，根为 0；不得改为自身或自身后代 */
    @NotNull(message = "parentId 不能为空")
    private Long parentId;

    @NotBlank(message = "deptName 不能为空")
    @jakarta.validation.constraints.Size(max = 50, message = "deptName 长度不能超过 50 个字符")
    private String deptName;

    /** 排序号 */
    private Integer sort;

    /** 0 停用 / 1 正常 */
    @Min(value = 0, message = "status 仅允许 0 或 1")
    @Max(value = 1, message = "status 仅允许 0 或 1")
    private Integer status;
}
