package com.smartwms.module.system.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 用户详情出参（API-USR-003）：基础信息 + 角色/仓库分配回显。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserDetailVO extends UserVO {

    /** 分配的角色 id 列表 */
    private List<Long> roleIds;
    /** 授权仓库 id 列表 */
    private List<Long> warehouseIds;
}
