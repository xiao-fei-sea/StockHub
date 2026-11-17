package com.smartwms.module.partner.dto;

import com.smartwms.common.PageQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 客户分页查询入参（API-CUS-001）。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerPageQueryDTO extends PageQueryDTO {

    /** 模糊查询：customerCode / customerName / contactName */
    private String keyword;

    /** 状态：0 停用 / 1 正常 */
    private Integer status;
}
