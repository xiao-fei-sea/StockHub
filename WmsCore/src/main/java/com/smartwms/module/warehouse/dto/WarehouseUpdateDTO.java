package com.smartwms.module.warehouse.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 修改仓库入参（API-WH-004）。
 * warehouseCode 可修改但须唯一（排除自身，重复 40904）；
 * status/sort 为 null 时保持原值（区别于新增的默认值兜底）。
 */
@Data
public class WarehouseUpdateDTO {

    @NotBlank(message = "warehouseCode 不能为空")
    @Size(max = 50, message = "warehouseCode 长度不能超过 50 个字符")
    private String warehouseCode;

    @NotBlank(message = "warehouseName 不能为空")
    @Size(max = 100, message = "warehouseName 长度不能超过 100 个字符")
    private String warehouseName;

    @Size(max = 255, message = "address 长度不能超过 255 个字符")
    private String address;

    @Size(max = 50, message = "contactName 长度不能超过 50 个字符")
    private String contactName;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$|^0\\d{2,3}-?\\d{7,8}$", message = "contactPhone 格式不正确")
    private String contactPhone;

    @PositiveOrZero(message = "areaSize 不能为负数")
    @Digits(integer = 10, fraction = 2, message = "areaSize 最多 2 位小数")
    private BigDecimal areaSize;

    @Min(value = 0, message = "status 仅允许 0 或 1")
    @Max(value = 1, message = "status 仅允许 0 或 1")
    private Integer status;

    private Integer sort;

    @Size(max = 500, message = "remark 长度不能超过 500 个字符")
    private String remark;
}
