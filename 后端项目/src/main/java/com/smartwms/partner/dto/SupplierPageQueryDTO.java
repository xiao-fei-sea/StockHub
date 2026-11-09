package com.smartwms.partner.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

/** 供应商分页查询入参（API-SUP-001）。 */
@Data
public class SupplierPageQueryDTO {

    @Min(value = 1, message = "pageNum 必须大于等于 1")
    private Integer pageNum = 1;

    @Min(value = 1, message = "pageSize 必须大于等于 1")
    private Integer pageSize = 20;

    /** 模糊查询：supplierCode / supplierName / contactName */
    private String keyword;

    /** 状态：0 停用 / 1 正常 */
    private Integer status;
}
