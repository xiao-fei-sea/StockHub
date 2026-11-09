package com.smartwms.storage.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 库区分页查询入参（API-AREA-001）。
 * warehouseId 文档标注「建议必填、待确认」，实现取可选：缺省时按数据仓库权限全量过滤。
 */
@Data
public class AreaPageQueryDTO {

    /** 页码，默认 1；小于 1 返回 40001 */
    @Min(value = 1, message = "pageNum 必须大于等于 1")
    private Integer pageNum = 1;

    /** 每页条数，默认 20；超过 100 由服务端按 100 处理 */
    @Min(value = 1, message = "pageSize 必须大于等于 1")
    private Integer pageSize = 20;

    /** 仓库 id；传入越权仓库返回 40302 */
    private Long warehouseId;

    /** 模糊查询：areaCode / areaName */
    private String keyword;

    /** 库区类型中文标签：存储区/拣货区/暂存区（非法标签 40001） */
    private String areaType;

    /** 状态过滤：0 停用 / 1 正常 */
    private Integer status;
}
