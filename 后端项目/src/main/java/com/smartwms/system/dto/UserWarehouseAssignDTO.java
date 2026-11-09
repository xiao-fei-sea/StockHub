package com.smartwms.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 分配用户仓库数据权限入参（API-USR-011）：整体替换 sys_user_warehouse，空数组表示清空授权。 */
@Data
public class UserWarehouseAssignDTO {

    /** 仓库 id 数组；必须显式提交（可为空数组表示清空授权） */
    @NotNull(message = "warehouseIds 不能为空")
    private List<Long> warehouseIds;
}
