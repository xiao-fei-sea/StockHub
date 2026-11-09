package com.smartwms.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 重置用户密码入参（API-USR-007）。
 * 复杂度与新增用户一致：≥8 位且含字母与数字（待确认项见 UserCreateDTO 注释）。
 */
@Data
public class UserResetPasswordDTO {

    @NotBlank(message = "password 不能为空")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
            message = "password 至少 8 位且需包含字母与数字")
    private String password;
}
