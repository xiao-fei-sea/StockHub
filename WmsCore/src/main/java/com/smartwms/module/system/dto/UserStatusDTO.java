package com.smartwms.module.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 启用/停用用户入参（API-USR-006）。 */
@Data
public class UserStatusDTO {

    /** 目标状态：0 停用 / 1 正常 */
    @NotNull(message = "status 不能为空")
    @Min(value = 0, message = "status 仅允许 0 或 1")
    @Max(value = 1, message = "status 仅允许 0 或 1")
    private Integer status;
}
