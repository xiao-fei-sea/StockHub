package com.smartwms.storage.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增货架入参（API-RACK-002）。
 * rackCode 同库区内唯一（40904），建议格式 JH-+自定义编号；层数/列数 ≥1，用于货位批量生成。
 */
@Data
public class RackCreateDTO {

    @NotNull(message = "warehouseId 不能为空")
    private Long warehouseId;

    @NotNull(message = "areaId 不能为空")
    private Long areaId;

    @NotBlank(message = "rackCode 不能为空")
    @Size(max = 50, message = "rackCode 长度不能超过 50 个字符")
    private String rackCode;

    @NotBlank(message = "rackName 不能为空")
    @Size(max = 100, message = "rackName 长度不能超过 100 个字符")
    private String rackName;

    /** 层数，正整数，默认 1；上限按批量生成单次上限（500）间接约束 */
    @Min(value = 1, message = "floorCount 必须大于等于 1")
    @Max(value = 500, message = "floorCount 不能超过 500")
    private Integer floorCount = 1;

    /** 列数，正整数，默认 1 */
    @Min(value = 1, message = "columnCount 必须大于等于 1")
    @Max(value = 500, message = "columnCount 不能超过 500")
    private Integer columnCount = 1;

    @Min(value = 0, message = "status 仅允许 0 或 1")
    @Max(value = 1, message = "status 仅允许 0 或 1")
    private Integer status = 1;
}
