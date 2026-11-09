package com.smartwms.system.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 用户分页查询入参（API-USR-001）。
 * 空值条件视为不过滤；pageSize 超过 100 由服务端按 100 处理。
 */
@Data
public class UserPageQueryDTO {

    /** 页码，默认 1；小于 1 返回 40001 */
    @Min(value = 1, message = "pageNum 必须大于等于 1")
    private Integer pageNum = 1;

    /** 每页条数，默认 20；超过 100 由服务端按 100 处理（接口文档 USR-001） */
    @Min(value = 1, message = "pageSize 必须大于等于 1")
    private Integer pageSize = 20;

    /** 模糊查询：用户名、姓名、手机号 */
    private String keyword;

    /** 状态过滤：0 停用 / 1 正常 */
    private Integer status;

    /** 部门 id，精确匹配 */
    private Long deptId;
}
