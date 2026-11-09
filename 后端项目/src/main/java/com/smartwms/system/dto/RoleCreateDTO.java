package com.smartwms.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 新增角色入参（API-ROLE-002）。
 * menuIds 可选，创建时同步初始化 sys_role_menu。
 */
@Data
public class RoleCreateDTO {

    /** 角色名称，必填，同内不重复 */
    @NotBlank(message = "roleName 不能为空")
    @Size(max = 50, message = "roleName 不能超过 50 字符")
    private String roleName;

    /** 角色权限字符，必填且全局唯一（如 ROLE_OPERATOR） */
    @NotBlank(message = "roleKey 不能为空")
    @Size(max = 50, message = "roleKey 不能超过 50 字符")
    private String roleKey;

    /** 显示排序，默认 0 */
    private Integer roleSort;

    /** 状态：0 停用 / 1 正常，默认 1 */
    @Min(value = 0, message = "status 取值非法")
    @Max(value = 1, message = "status 取值非法")
    private Integer status;

    /** 菜单权限 id 集合（目录/菜单/按钮），写入 sys_role_menu */
    private List<Long> menuIds;

    /** 备注 */
    @Size(max = 500, message = "remark 不能超过 500 字符")
    private String remark;
}
