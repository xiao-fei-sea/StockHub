package com.smartwms.module.system.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 角色列表 VO（API-ROLE-001），createBy/updateBy 为用户名。 */
@Data
public class RoleVO {

    private Long id;
    private String roleName;
    private String roleKey;
    private Integer roleSort;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String createBy;
    private String updateBy;
}
