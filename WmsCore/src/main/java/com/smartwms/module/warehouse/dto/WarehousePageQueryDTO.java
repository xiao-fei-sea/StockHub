package com.smartwms.module.warehouse.dto;

import com.smartwms.common.PageQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 仓库分页查询入参（API-WH-001）。
 * 空值条件视为不过滤；pageSize 超过 100 由服务端按 100 处理。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WarehousePageQueryDTO extends PageQueryDTO {

    /** 模糊查询：warehouseCode / warehouseName / address */
    private String keyword;

    /** 状态过滤：0 停用 / 1 正常 */
    private Integer status;
}
