package com.smartwms.storage.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 修改货位入参（API-LOC-003）。
 * 有库存的货位禁止变更层级（40901）、人工禁止置「占用」（40901）、
 * 有库存时禁止置禁用/停用（40906）。
 */
@Data
public class LocationUpdateDTO {

    @NotNull(message = "warehouseId 不能为空")
    private Long warehouseId;

    @NotNull(message = "areaId 不能为空")
    private Long areaId;

    @NotNull(message = "rackId 不能为空")
    private Long rackId;

    @NotBlank(message = "locationCode 不能为空")
    @Size(max = 50, message = "locationCode 长度不能超过 50 个字符")
    private String locationCode;

    @NotBlank(message = "locationName 不能为空")
    @Size(max = 100, message = "locationName 长度不能超过 100 个字符")
    private String locationName;

    /** 货位类型中文标签：存储位/拣货位/暂存位/收货位 */
    @NotBlank(message = "locationType 不能为空")
    private String locationType;

    /** 容积（m³），非负且最多 3 位小数 */
    @PositiveOrZero(message = "capacityVolume 不能为负数")
    @Digits(integer = 9, fraction = 3, message = "capacityVolume 最多 3 位小数")
    private BigDecimal capacityVolume;

    /** 货位状态中文标签；null 表示不变；人工仅可设 空闲/锁定/禁用 */
    private String currentStatus;

    /** 启用状态 0 停用 / 1 正常；null 表示不变 */
    @Min(value = 0, message = "status 仅允许 0 或 1")
    @Max(value = 1, message = "status 仅允许 0 或 1")
    private Integer status;

    /** 显示顺序；null 表示不变 */
    private Integer sort;
}
