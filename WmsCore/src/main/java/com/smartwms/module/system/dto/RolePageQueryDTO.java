package com.smartwms.module.system.dto;

import com.smartwms.common.PageQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色分页查询入参（API-ROLE-001）。
 * 空值条件视为不过滤；pageSize 超过 100 由服务端按 100 处理。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RolePageQueryDTO extends PageQueryDTO {

    /** 模糊关键字，匹配 roleName / roleKey */
    private String keyword;

    /** 状态过滤：0 停用 / 1 正常 */
    private Integer status;
}
