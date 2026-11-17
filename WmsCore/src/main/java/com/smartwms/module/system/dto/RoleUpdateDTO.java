package com.smartwms.module.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改角色入参（API-ROLE-003）。
 * 本接口不修改 menuIds（权限集合走 API-ROLE-006 整体替换）。
 */
@Data
public class RoleUpdateDTO {

    /** 角色名称，必填 */
    @NotBlank(message = "roleName 不能为空")
    @Size(max = 50, message = "roleName 不能超过 50 字符")
    private String roleName;

    /** 角色权限字符，必填且唯一（排除自身） */
    @NotBlank(message = "roleKey 不能为空")
    @Size(max = 50, message = "roleKey 不能超过 50 字符")
    private String roleKey;

    /** 显示排序 */
    private Integer roleSort;

    /** 状态：0 停用 / 1 正常 */
    @Min(value = 0, message = "status 取值非法")
    @Max(value = 1, message = "status 取值非法")
    private Integer status;

    /** 备注 */
    @Size(max = 500, message = "remark 不能超过 500 字符")
    private String remark;
}
