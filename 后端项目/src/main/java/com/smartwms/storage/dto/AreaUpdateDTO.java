package com.smartwms.storage.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改库区入参（API-AREA-003）。
 * areaCode 同仓库内唯一（排除自身）；已有库存的库区禁止变更 warehouseId（40901）。
 */
@Data
public class AreaUpdateDTO {

    @NotNull(message = "warehouseId 不能为空")
    private Long warehouseId;

    @NotBlank(message = "areaCode 不能为空")
    @Size(max = 50, message = "areaCode 长度不能超过 50 个字符")
    private String areaCode;

    @NotBlank(message = "areaName 不能为空")
    @Size(max = 100, message = "areaName 长度不能超过 100 个字符")
    private String areaName;

    /** 库区类型中文标签：存储区/拣货区/暂存区 */
    @NotBlank(message = "areaType 不能为空")
    private String areaType;

    /** 状态 0 停用 / 1 正常 */
    @Min(value = 0, message = "status 仅允许 0 或 1")
    @Max(value = 1, message = "status 仅允许 0 或 1")
    private Integer status;
}
