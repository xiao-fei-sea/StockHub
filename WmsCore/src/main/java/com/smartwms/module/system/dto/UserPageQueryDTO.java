package com.smartwms.module.system.dto;

import com.smartwms.common.PageQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户分页查询入参（API-USR-001）。
 * 空值条件视为不过滤；pageSize 超过 100 由服务端按 100 处理。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserPageQueryDTO extends PageQueryDTO {

    /** 模糊查询：用户名、姓名、手机号 */
    private String keyword;

    /** 状态过滤：0 停用 / 1 正常 */
    private Integer status;

    /** 部门 id，精确匹配 */
    private Long deptId;
}
