package com.smartwms.module.partner.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增客户入参（API-CUS-002）。
 * customerCode 全局唯一（40904）；无 bankInfo 字段（与供应商表差异）。
 */
@Data
public class CustomerCreateDTO {

    @NotBlank(message = "customerCode 不能为空")
    @Size(max = 50, message = "customerCode 长度不能超过 50 个字符")
    private String customerCode;

    @NotBlank(message = "customerName 不能为空")
    @Size(max = 100, message = "customerName 长度不能超过 100 个字符")
    private String customerName;

    @Size(max = 50, message = "contactName 长度不能超过 50 个字符")
    private String contactName;

    /** 联系电话：11 位手机号或座机（选填） */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$|^0\\d{2,3}-?\\d{7,8}$",
            message = "contactPhone 格式不正确（11 位手机号或座机）")
    @Size(max = 20, message = "contactPhone 长度不能超过 20 个字符")
    private String contactPhone;

    @Email(message = "email 格式不正确")
    @Size(max = 100, message = "email 长度不能超过 100 个字符")
    private String email;

    @Size(max = 255, message = "address 长度不能超过 255 个字符")
    private String address;

    @Min(value = 0, message = "status 仅允许 0 或 1")
    @Max(value = 1, message = "status 仅允许 0 或 1")
    private Integer status = 1;

    @Size(max = 500, message = "remark 长度不能超过 500 个字符")
    private String remark;
}
