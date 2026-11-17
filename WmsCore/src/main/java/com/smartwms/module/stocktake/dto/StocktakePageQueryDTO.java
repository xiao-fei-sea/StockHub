package com.smartwms.module.stocktake.dto;

import com.smartwms.common.PageQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 盘点单分页查询入参（API-STK-001）。
 * pageSize 超过 100 截断为 100；status 0~5；diffStatus 中文标签（非法 40001）；
 * keyword 覆盖单号与仓库名；startDate ≤ endDate（40001）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class StocktakePageQueryDTO extends PageQueryDTO {

    private Integer status;

    private Long warehouseId;

    private Long areaId;

    /** 盘点单号（模糊） */
    private String stocktakeNo;

    /** 关键字（单号、仓库名） */
    private String keyword;

    /** 差异处理状态中文标签：未处理 / 已处理 */
    private String diffStatus;

    private String startDate;

    private String endDate;
}
