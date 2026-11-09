package com.smartwms.system.service;

import com.smartwms.system.dto.LoginDTO;
import com.smartwms.system.dto.LogoutDTO;
import com.smartwms.system.dto.RefreshDTO;
import com.smartwms.system.dto.RegisterDTO;
import com.smartwms.system.vo.AuthInfoVO;
import com.smartwms.system.vo.LoginVO;

/**
 * 认证服务（API-AUTH-001~005）。
 * 业务规则见《用户管理接口文档》1.4：40101 统一“账号或密码错误”、40102 停用、
 * refresh 失败返回 401、登出幂等。
 */
public interface AuthService {

    /** 注册（API-AUTH-005，免认证）：账号唯一校验 → BCrypt 存储 → 默认启用并绑定默认角色。 */
    void register(RegisterDTO dto);

    /** 登录：校验账号密码 → 更新 login_date → 签发 access/refresh 双令牌。 */
    LoginVO login(LoginDTO dto);

    /** 刷新令牌：refreshToken 合法且用户启用 → 签发新双令牌（旧令牌自然过期）。 */
    LoginVO refresh(RefreshDTO dto);

    /** 登出：无状态会话，幂等返回成功。 */
    void logout(LogoutDTO dto);

    /** 当前登录用户信息：用户 + 角色 + 权限 + 菜单树 + 数据仓库范围。 */
    AuthInfoVO info();
}
