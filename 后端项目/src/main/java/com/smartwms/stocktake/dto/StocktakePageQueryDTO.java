package com.smartwms.stocktake.dto;

import lombok.Data;

/**
 * 盘点单分页查询入参（API-STK-001）。
 * pageSize 上限 100（超出 40001）；status 0~5；diffStatus 中文标签（非法 40001）；
 * keyword 覆盖单号与仓库名；startDate ≤ endDate（40001）。
 */
@Data
public class StocktakePageQueryDTO {

    private Integer pageNum = 1;

    private Integer pageSize = 20;

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
