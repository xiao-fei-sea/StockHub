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
 * 批量生成货位入参（API-LOC-006）。
 * 生成编码 = prefix + 层号2位 + 列号2位（全局唯一，50 字符内 → prefix ≤ 46）；
 * floorCount×columnCount ≤ 500（单次批量上限，防误操作）；单事务插入，失败整体回滚。
 */
@Data
public class LocationBatchGenerateDTO {

    @NotNull(message = "warehouseId 不能为空")
    private Long warehouseId;

    @NotNull(message = "areaId 不能为空")
    private Long areaId;

    @NotNull(message = "rackId 不能为空")
    private Long rackId;

    /** 编码前缀，缺省按货架编码推导（JH-xxx → HW-0xxx）；生成码 = prefix + 层2位 + 列2位 */
    @Size(max = 46, message = "prefix 长度不能超过 46 个字符")
    private String prefix;

    /** 起始层号，默认 1 */
    @Min(value = 1, message = "startFloor 必须大于等于 1")
    @Max(value = 99, message = "startFloor 不能超过 99")
    private Integer startFloor = 1;

    /** 起始列号，默认 1 */
    @Min(value = 1, message = "startColumn 必须大于等于 1")
    @Max(value = 99, message = "startColumn 不能超过 99")
    private Integer startColumn = 1;

    /** 生成层数，缺省取货架 floorCount；floorCount×columnCount ≤ 500 */
    @Min(value = 1, message = "floorCount 必须大于等于 1")
    @Max(value = 500, message = "floorCount 不能超过 500")
    private Integer floorCount;

    /** 生成列数，缺省取货架 columnCount */
    @Min(value = 1, message = "columnCount 必须大于等于 1")
    @Max(value = 500, message = "columnCount 不能超过 500")
    private Integer columnCount;

    /** 统一货位类型中文标签（必填，非法标签服务层返回 40001） */
    @NotBlank(message = "locationType 不能为空")
    private String locationType;

    /** 统一容积（m³），缺省为空 */
    @PositiveOrZero(message = "capacityVolume 不能为负数")
    @Digits(integer = 9, fraction = 3, message = "capacityVolume 最多 3 位小数")
    private BigDecimal capacityVolume;

    /** 遇已存在编码是否跳过，默认 true；false 时任一冲突整体报 40904 */
    private Boolean skipExists = true;
}
