package com.smartwms.module.inbound.dto;

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
 * 修改入库单入参（API-INB-003）。
 * 仅草稿/已驳回可改（40901）；已驳回保存后回草稿（4→0）；version 乐观锁（40905）。
 */
@Data
public class InboundUpdateDTO {

    @NotBlank(message = "orderType 不能为空")
    private String orderType;

    @NotNull(message = "warehouseId 不能为空")
    private Long warehouseId;

    private Long supplierId;

    @Size(max = 50, message = "sourceOrderNo 长度不能超过 50 个字符")
    private String sourceOrderNo;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expectDate;

    @Size(max = 500, message = "remark 长度不能超过 500 个字符")
    private String remark;

    /** 乐观锁版本号（取详情响应），不一致返回 40905 */
    @NotNull(message = "version 不能为空")
    private Integer version;

    @NotEmpty(message = "明细不能为空")
    @Size(max = 200, message = "明细行数不能超过 200 条")
    @Valid
    private List<InboundItemDTO> items;
}
