package com.smartwms.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 登录入参（API-AUTH-001）。 */
@Data
public class LoginDTO {

    @NotBlank(message = "username 不能为空")
    private String username;

    @NotBlank(message = "password 不能为空")
    private String password;
}
