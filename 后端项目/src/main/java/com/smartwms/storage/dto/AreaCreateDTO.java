package com.smartwms.storage.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增库区入参（API-AREA-002）。
 * areaCode 同仓库内唯一（40904），建议格式 QY-+自定义编号（基线第 10 节，非强制）。
 */
@Data
public class AreaCreateDTO {

    @NotNull(message = "warehouseId 不能为空")
    private Long warehouseId;

    @NotBlank(message = "areaCode 不能为空")
    @Size(max = 50, message = "areaCode 长度不能超过 50 个字符")
    private String areaCode;

    @NotBlank(message = "areaName 不能为空")
    @Size(max = 100, message = "areaName 长度不能超过 100 个字符")
    private String areaName;

    /** 库区类型中文标签：存储区/拣货区/暂存区（非法标签服务层返回 40001） */
    @NotBlank(message = "areaType 不能为空")
    private String areaType;

    /** 状态 0 停用 / 1 正常，缺省默认 1 */
    @Min(value = 0, message = "status 仅允许 0 或 1")
    @Max(value = 1, message = "status 仅允许 0 或 1")
    private Integer status = 1;
}
