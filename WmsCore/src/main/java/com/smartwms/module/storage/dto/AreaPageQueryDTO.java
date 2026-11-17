package com.smartwms.module.storage.dto;

import com.smartwms.common.PageQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 库区分页查询入参（API-AREA-001）。
 * warehouseId 文档标注「建议必填、待确认」，实现取可选：缺省时按数据仓库权限全量过滤。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AreaPageQueryDTO extends PageQueryDTO {

    /** 仓库 id；传入越权仓库返回 40302 */
    private Long warehouseId;

    /** 模糊查询：areaCode / areaName */
    private String keyword;

    /** 库区类型中文标签：存储区/拣货区/暂存区（非法标签 40001） */
    private String areaType;

    /** 状态过滤：0 停用 / 1 正常 */
    private Integer status;
}
