package com.smartwms.module.partner.dto;

import com.smartwms.common.PageQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 供应商分页查询入参（API-SUP-001）。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SupplierPageQueryDTO extends PageQueryDTO {

    /** 模糊查询：supplierCode / supplierName / contactName */
    private String keyword;

    /** 状态：0 停用 / 1 正常 */
    private Integer status;
}
