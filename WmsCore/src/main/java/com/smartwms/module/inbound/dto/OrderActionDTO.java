package com.smartwms.module.inbound.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 单据动作通用入参（INB-005~009 以及后续出库/盘点/调拨动作接口复用）。
 * 提交/审核可附 remark；驳回 reason 必填（服务层校验 40001）；
 * version 乐观锁（取详情响应），不一致返回 40905。
 */
@Data
public class OrderActionDTO {

    @Size(max = 500, message = "remark 长度不能超过 500 个字符")
    private String remark;

    /** 驳回原因（INB-007 必填，服务层校验） */
    @Size(max = 200, message = "reason 长度不能超过 200 个字符")
    private String reason;

    @NotNull(message = "version 不能为空")
    private Integer version;
}
