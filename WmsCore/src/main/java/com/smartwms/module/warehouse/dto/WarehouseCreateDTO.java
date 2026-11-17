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
 * 新增仓库入参（API-WH-002）。
 * warehouseCode 全局唯一（40904），建议格式 WH-+自定义编号（基线第 10 节，非强制）。
 */
@Data
public class WarehouseCreateDTO {

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

    /** 联系电话，可选；填写时须为 11 位手机号或座机格式 */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$|^0\\d{2,3}-?\\d{7,8}$", message = "contactPhone 格式不正确")
    private String contactPhone;

    /** 面积（㎡），非负且与库表 DECIMAL(12,2) 一致最多 2 位小数 */
    @PositiveOrZero(message = "areaSize 不能为负数")
    @Digits(integer = 10, fraction = 2, message = "areaSize 最多 2 位小数")
    private BigDecimal areaSize;

    /** 状态 0 停用 / 1 正常，缺省默认 1（服务端兜底） */
    @Min(value = 0, message = "status 仅允许 0 或 1")
    @Max(value = 1, message = "status 仅允许 0 或 1")
    private Integer status = 1;

    /** 排序号，缺省默认 0（服务端兜底） */
    private Integer sort = 0;

    @Size(max = 500, message = "remark 长度不能超过 500 个字符")
    private String remark;
}
