package com.smartwms.module.inbound.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.smartwms.common.PageQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 入库单分页查询入参（API-INB-001）。
 * 查询结果按数据仓库权限过滤（40302）；默认按创建时间倒序。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InboundPageQueryDTO extends PageQueryDTO {

    private Long warehouseId;

    /** 入库单号前缀模糊匹配 */
    private String inboundNo;

    /** 入库类型中文标签：采购入库/调拨入库/退货入库/盘盈入库（非法标签 40001） */
    private String orderType;

    private Long supplierId;

    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expectDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate createTimeFrom;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate createTimeTo;

    private String keyword;
}
