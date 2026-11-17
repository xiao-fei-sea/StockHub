package com.smartwms.module.outbound.dto;

import com.smartwms.common.PageQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 出库单分页查询入参（API-OUT-001）。
 * 查询结果按数据仓库权限过滤（40302）；默认按创建时间倒序；
 * pageSize 上限 100；status 取值 0~5；startDate ≤ endDate（服务层校验 40001）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OutboundPageQueryDTO extends PageQueryDTO {

    private Long warehouseId;

    /** 出库单号模糊匹配 */
    private String outboundNo;

    private Integer status;

    /** 出库类型中文标签：销售出库/调拨出库/退货出库/报损出库（非法标签 40001） */
    private String orderType;

    /** 关键字（单号、客户名称、来源单号） */
    private String keyword;

    /** 创建开始日期 yyyy-MM-dd（含当天） */
    private String startDate;

    /** 创建结束日期 yyyy-MM-dd（含当天） */
    private String endDate;
}
