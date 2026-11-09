package com.smartwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 角色表 sys_role。 */
@Data
@TableName("sys_role")
public class SysRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String roleName;
    /** 角色标识：ROLE_ADMIN / ROLE_MANAGER / ROLE_OPERATOR / ROLE_STOCKTAKE / ROLE_VIEWER */
    private String roleKey;
    private Integer roleSort;
    /** 状态：0 停用 / 1 启用 */
    private Integer status;
    /** 备注 */
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;
    private Integer deleted;
}
