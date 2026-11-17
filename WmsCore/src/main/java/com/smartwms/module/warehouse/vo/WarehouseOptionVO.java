package com.smartwms.module.warehouse.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 仓库下拉选项出参（API-WH-006）：精简字段，不分页，默认仅启用仓库。
 */
@Data
@Builder
public class WarehouseOptionVO {

    private Long id;
    private String warehouseCode;
    private String warehouseName;
}
