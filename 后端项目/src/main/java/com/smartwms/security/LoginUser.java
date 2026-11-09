package com.smartwms.security;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.Set;

/**
 * 当前登录用户上下文（安全层）：由 JWT 过滤器解析令牌后加载并放入 SecurityContext。
 * 仅承载认证与授权所需信息，不作为接口出参（出参用 VO）。
 */
@Data
@NoArgsConstructor
public class LoginUser {

    private Long id;
    private String username;
    private String realName;
    /** 角色标识集合，如 ROLE_ADMIN（仅启用角色） */
    private Set<String> roles = Collections.emptySet();
    /** 按钮/菜单权限标识集合，如 sys:user:add（仅启用菜单） */
    private Set<String> permissions = Collections.emptySet();
    /** 授权仓库 ID 集合；warehouseUnrestricted=true 时本集合不参与过滤 */
    private Set<Long> warehouseIds = Collections.emptySet();
    /** 数据仓库权限是否不受限（ROLE_ADMIN、ROLE_VIEWER 口径，基线第 5 节） */
    private boolean warehouseUnrestricted;

    public LoginUser(Long id, String username) {
        this.id = id;
        this.username = username;
    }

    /** 权限判定（供 @perm.has 与业务校验使用）。 */
    public boolean hasPermission(String perm) {
        return permissions != null && permissions.contains(perm);
    }

    @Override
    public String toString() {
        // 使 Authentication.getName() 返回账号而非对象地址
        return username;
    }
}
