package com.smartwms.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改用户入参（API-USR-004）：仅基础信息，不含密码、角色、授权仓库
 * （三者分别走 API-USR-007/008/011）。
 */
@Data
public class UserUpdateDTO {

    @NotBlank(message = "username 不能为空")
    @Size(min = 2, max = 50, message = "username 长度须为 2~50 个字符")
    private String username;

    @NotBlank(message = "realName 不能为空")
    @Size(max = 50, message = "realName 长度不能超过 50 个字符")
    private String realName;

    @Pattern(regexp = "^$|^1\\d{10}$", message = "phone 须为 11 位手机号")
    private String phone;

    @Pattern(regexp = "^$|^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$", message = "email 格式不正确")
    private String email;

    /** 部门 id，可选 */
    private Long deptId;

    /** 0 停用 / 1 正常，可选 */
    @Min(value = 0, message = "status 仅允许 0 或 1")
    @Max(value = 1, message = "status 仅允许 0 或 1")
    private Integer status;
}
