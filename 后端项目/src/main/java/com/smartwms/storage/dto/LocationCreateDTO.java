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
 * 新增货位入参（API-LOC-002）。
 * locationCode 全局唯一（40904），建议 HW-+层列序号（基线第 10 节）；
 * 层级自洽：rackId 属于 areaId、areaId 属于 warehouseId，否则 40001。
 */
@Data
public class LocationCreateDTO {

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

    /** 货位类型中文标签：存储位/拣货位/暂存位/收货位（非法标签服务层返回 40001） */
    @NotBlank(message = "locationType 不能为空")
    private String locationType;

    /** 容积（m³），非负且最多 3 位小数（与 DECIMAL(12,3) 一致） */
    @PositiveOrZero(message = "capacityVolume 不能为负数")
    @Digits(integer = 9, fraction = 3, message = "capacityVolume 最多 3 位小数")
    private BigDecimal capacityVolume;

    /** 货位状态中文标签，缺省「空闲」；「占用」由库存联动，人工不可设置（40901） */
    private String currentStatus = "空闲";

    /** 启用状态 0 停用 / 1 正常，缺省默认 1 */
    @Min(value = 0, message = "status 仅允许 0 或 1")
    @Max(value = 1, message = "status 仅允许 0 或 1")
    private Integer status = 1;

    /** 显示顺序，缺省默认 0 */
    private Integer sort = 0;
}
