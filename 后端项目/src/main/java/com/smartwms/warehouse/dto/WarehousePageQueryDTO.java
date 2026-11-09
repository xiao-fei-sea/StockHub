package com.smartwms.warehouse.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 仓库分页查询入参（API-WH-001）。
 * 空值条件视为不过滤；pageSize 超过 100 由服务端按 100 处理。
 */
@Data
public class WarehousePageQueryDTO {

    /** 页码，默认 1；小于 1 返回 40001 */
    @Min(value = 1, message = "pageNum 必须大于等于 1")
    private Integer pageNum = 1;

    /** 每页条数，默认 20；超过 100 由服务端按 100 处理（接口文档 WH-001） */
    @Min(value = 1, message = "pageSize 必须大于等于 1")
    private Integer pageSize = 20;

    /** 模糊查询：warehouseCode / warehouseName / address */
    private String keyword;

    /** 状态过滤：0 停用 / 1 正常 */
    private Integer status;
}
