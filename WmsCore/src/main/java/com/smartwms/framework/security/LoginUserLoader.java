package com.smartwms.framework.security;

/**
 * 登录用户加载器（安全层定义、system 模块实现）：
 * 依赖倒置，避免公共支撑包直接依赖业务模块（《后端开发规范》第 2.2 节）。
 */
public interface LoginUserLoader {

    /**
     * 按用户 ID 加载完整登录上下文（角色、权限、仓库范围）。
     *
     * @return 用户不存在、已删除或已停用时返回 null（过滤器将保持未认证，入口返回 401）
     */
    LoginUser loadLoginUser(Long userId, String username);
}
