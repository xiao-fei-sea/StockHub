package com.smartwms.module.inventory.dto;

import com.smartwms.common.PageQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 库存变动流水分页查询入参（API-INV-002）。
 * flowType 传中文标签（非法 40001）、direction 为 IN/OUT；
 * startDate/endDate 为 yyyy-MM-dd（服务层校验格式与先后，40001）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FlowPageQueryDTO extends PageQueryDTO {

    private Long warehouseId;

    private Long productId;

    /** 流水类型中文标签：入库/出库/调拨入/调拨出/盘盈/盘亏/调整 */
    private String flowType;

    /** 方向：IN / OUT */
    private String direction;

    /** 关联单号（模糊匹配，如 RK20261106001、CK20261106001） */
    private String refNo;

    /** 操作开始日期 yyyy-MM-dd（含当日 00:00:00） */
    private String startDate;

    /** 操作结束日期 yyyy-MM-dd（含当日 23:59:59） */
    private String endDate;
}
