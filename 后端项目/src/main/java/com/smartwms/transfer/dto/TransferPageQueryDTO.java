package com.smartwms.transfer.dto;

import lombok.Data;

/**
 * 调拨单分页查询入参（API-TRF-001）。
 * 查询结果按数据仓库权限过滤（源仓或目标仓任一在权限内可见，13.7 待确认采纳）；
 * pageSize 上限 100；status 取值 0~5；startDate ≤ endDate（服务层校验 40001）。
 */
@Data
public class TransferPageQueryDTO {

    private Integer pageNum = 1;

    private Integer pageSize = 20;

    /** 源仓库 ID */
    private Long srcWarehouseId;

    /** 目标仓库 ID */
    private Long destWarehouseId;

    /** 调拨单号模糊匹配 */
    private String transferNo;

    private Integer status;

    /** 关键字（单号、仓库名称） */
    private String keyword;

    /** 创建开始日期 yyyy-MM-dd（含当天） */
    private String startDate;

    /** 创建结束日期 yyyy-MM-dd（含当天） */
    private String endDate;
}
