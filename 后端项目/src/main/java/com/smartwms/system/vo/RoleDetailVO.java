package com.smartwms.system.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 角色详情 VO（API-ROLE-005），menuIds 供权限配置界面树形勾选回显。 */
@Data
public class RoleDetailVO {

    private Long id;
    private String roleName;
    private String roleKey;
    private Integer roleSort;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    /** 已授权菜单 id 集合（含目录/菜单/按钮），升序 */
    private List<Long> menuIds;
}
