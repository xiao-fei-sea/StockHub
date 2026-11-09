package com.smartwms.transfer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 新增调拨单入参（API-TRF-002）。
 * srcWarehouseId ≠ destWarehouseId（服务层校验 40001）；两仓均须在数据权限内（40302）；
 * 明细 ≤200 行；totalQty/actualQty 由服务端汇总，status 初始草稿、confirm_status 初始未确认。
 */
@Data
public class TransferCreateDTO {

    @NotNull(message = "srcWarehouseId 不能为空")
    private Long srcWarehouseId;

    @NotNull(message = "destWarehouseId 不能为空")
    private Long destWarehouseId;

    @Size(max = 500, message = "remark 长度不能超过 500 个字符")
    private String remark;

    @NotEmpty(message = "明细不能为空")
    @Size(max = 200, message = "明细行数不能超过 200 条")
    @Valid
    private List<TransferItemDTO> items;
}
