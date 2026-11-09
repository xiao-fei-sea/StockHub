package com.smartwms.inbound.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 新增入库单入参（API-INB-002）。
 * orderType 中文枚举名（服务层转 TINYINT）；采购入库 supplierId 必填（40001，服务层校验）；
 * 明细 ≤200 行；totalQty/actualQty 由服务端汇总，status 初始草稿。
 */
@Data
public class InboundCreateDTO {

    @NotBlank(message = "orderType 不能为空")
    private String orderType;

    @NotNull(message = "warehouseId 不能为空")
    private Long warehouseId;

    /** 供应商 id；采购入库必填 */
    private Long supplierId;

    @Size(max = 50, message = "sourceOrderNo 长度不能超过 50 个字符")
    private String sourceOrderNo;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expectDate;

    @Size(max = 500, message = "remark 长度不能超过 500 个字符")
    private String remark;

    @NotEmpty(message = "明细不能为空")
    @Size(max = 200, message = "明细行数不能超过 200 条")
    @Valid
    private List<InboundItemDTO> items;
}
