package com.smartwms.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 分配角色菜单权限入参（API-ROLE-006），空数组表示清空该角色全部权限。 */
@Data
public class RoleMenuAssignDTO {

    /** 菜单 id 集合（目录/菜单/按钮均可）；必填，可为空数组 */
    @NotNull(message = "menuIds 不能为空")
    private List<Long> menuIds;
}
