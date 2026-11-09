package com.smartwms.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户注册入参（API-AUTH-005，免认证公开注册）。
 * 密码策略与《系统安全架构设计》第 4 节一致：长度 ≥ 8 位且含字母 + 数字。
 */
@Data
public class RegisterDTO {

    @NotBlank(message = "username 不能为空")
    @Size(min = 2, max = 50, message = "username 长度须为 2~50 个字符")
    private String username;

    @NotBlank(message = "password 不能为空")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
            message = "password 至少 8 位且需包含字母与数字")
    private String password;

    @NotBlank(message = "realName 不能为空")
    @Size(max = 50, message = "realName 长度不能超过 50 个字符")
    private String realName;

    /** 手机号，可选；填写时须为 11 位大陆手机号。 */
    @Pattern(regexp = "^$|^1\\d{10}$", message = "phone 须为 11 位手机号")
    private String phone;
}
