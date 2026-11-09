package com.smartwms.storage.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改货架入参（API-RACK-003）。
 * 已有货位时禁止变更 areaId/warehouseId（40901），
 * floorCount/columnCount 不得小于已生成货位覆盖的层数/列数（40901）。
 */
@Data
public class RackUpdateDTO {

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

    /** 层数；null 表示保持不变 */
    @Min(value = 1, message = "floorCount 必须大于等于 1")
    @Max(value = 500, message = "floorCount 不能超过 500")
    private Integer floorCount;

    /** 列数；null 表示保持不变 */
    @Min(value = 1, message = "columnCount 必须大于等于 1")
    @Max(value = 500, message = "columnCount 不能超过 500")
    private Integer columnCount;

    @Min(value = 0, message = "status 仅允许 0 或 1")
    @Max(value = 1, message = "status 仅允许 0 或 1")
    private Integer status;
}
