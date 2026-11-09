package com.smartwms.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 新增用户入参（API-USR-002）。
 * 密码策略按 ≥8 位含字母与数字统一执行（与注册 API-AUTH-005 一致；
 * 待确认：接口文档 USR-002 写「最少 6 位」，建议文档统一为 8 位口径）。
 */
@Data
public class UserCreateDTO {

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

    /** 手机号，可选；填写时须为 11 位大陆手机号 */
    @Pattern(regexp = "^$|^1\\d{10}$", message = "phone 须为 11 位手机号")
    private String phone;

    /** 邮箱，可选；填写时须为合法邮箱格式 */
    @Pattern(regexp = "^$|^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$", message = "email 格式不正确")
    private String email;

    /** 部门 id，可选；须存在 */
    private Long deptId;

    /** 状态 0 停用 / 1 正常，缺省默认 1（服务端兜底） */
    @Min(value = 0, message = "status 仅允许 0 或 1")
    @Max(value = 1, message = "status 仅允许 0 或 1")
    private Integer status = 1;

    /** 角色 id 数组，可选；元素须为存在的角色 */
    private List<Long> roleIds;

    /** 授权仓库 id 数组，可选；元素须为存在的仓库 */
    private List<Long> warehouseIds;
}
