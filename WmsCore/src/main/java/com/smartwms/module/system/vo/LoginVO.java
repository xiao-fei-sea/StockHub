package com.smartwms.module.system.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** 登录/刷新令牌响应（API-AUTH-001/002）。 */
@Data
@Builder
public class LoginVO {

    private String accessToken;
    private String refreshToken;
    /** 固定 Bearer */
    private String tokenType;
    /** access token 有效期（秒） */
    private Long expiresIn;
    /** 登录返回用户简要信息；刷新令牌返回 null（不序列化） */
    private TokenUserVO user;

    /** 登录响应中的用户信息（对应接口文档示例）。 */
    @Data
    @Builder
    public static class TokenUserVO {
        private Long id;
        private String username;
        private String realName;
        private Long deptId;
        private Integer status;
        private List<String> roles;
    }
}
