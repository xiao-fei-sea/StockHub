package com.smartwms.alert.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 处理预警入参（API-ALR-007）。
 * 记录表无 version 列，幂等以 status 状态校验（仅「未处理」可处理，重复 40901）；
 * 处理人/处理时间由服务端写入，不接受前端传值。
 */
@Data
public class AlertHandleDTO {

    @Size(max = 500, message = "handleRemark 长度不能超过 500 个字符")
    private String handleRemark;
}
