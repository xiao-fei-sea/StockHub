package com.smartwms.inbound.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDate;

/**
 * 入库单分页查询入参（API-INB-001）。
 * 查询结果按数据仓库权限过滤（40302）；默认按创建时间倒序。
 */
@Data
public class InboundPageQueryDTO {

    @Min(value = 1, message = "pageNum 必须大于等于 1")
    private Integer pageNum = 1;

    @Min(value = 1, message = "pageSize 必须大于等于 1")
    private Integer pageSize = 20;

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
