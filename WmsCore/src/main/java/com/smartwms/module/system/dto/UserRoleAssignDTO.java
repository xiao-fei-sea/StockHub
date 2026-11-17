package com.smartwms.module.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 分配用户角色入参（API-USR-008）：整体替换 sys_user_role，空数组表示清空角色。 */
@Data
public class UserRoleAssignDTO {

    /** 角色 id 数组；必须显式提交（可为空数组表示清空） */
    @NotNull(message = "roleIds 不能为空")
    private List<Long> roleIds;
}
