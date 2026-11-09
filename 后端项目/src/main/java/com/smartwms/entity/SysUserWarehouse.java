package com.smartwms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 用户-仓库数据权限表 sys_user_warehouse（唯一键 uk_user_warehouse）。 */
@Data
@TableName("sys_user_warehouse")
public class SysUserWarehouse {

    private Long id;
    private Long userId;
    private Long warehouseId;
}
