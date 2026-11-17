package com.smartwms.framework.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 按钮权限判定组件，供 Controller 的 @PreAuthorize("@perm.has('sys:xxx:yyy')") 引用。
 * 判定依据为 JWT 过滤器加载的 LoginUser.permissions（仅启用菜单的 perms）。
 */
@Component("perm")
public class PermEvaluator {

    /** 是否拥有指定按钮权限；ROLE_ADMIN 角色全权限放行（基线第 5 节），未登录返回 false。 */
    public boolean has(String perm) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser loginUser)) {
            return false;
        }
        if (loginUser.getRoles() != null && loginUser.getRoles().contains("ROLE_ADMIN")) {
            return true;
        }
        return loginUser.hasPermission(perm);
    }
}
