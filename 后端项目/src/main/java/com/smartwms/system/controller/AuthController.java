package com.smartwms.system.controller;

import com.smartwms.aspect.OperationLog;
import com.smartwms.system.dto.LoginDTO;
import com.smartwms.system.dto.LogoutDTO;
import com.smartwms.system.dto.RefreshDTO;
import com.smartwms.system.dto.RegisterDTO;
import com.smartwms.system.service.AuthService;
import com.smartwms.system.vo.AuthInfoVO;
import com.smartwms.system.vo.LoginVO;
import com.smartwms.vo.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口 API-AUTH-001~005（详见《用户管理接口文档》第 1 节）。
 * login/refresh/register 为免认证白名单（SecurityConfig）；logout/info 需 JWT 认证。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** API-AUTH-005 用户注册（无需认证；默认启用并绑定 ROLE_OPERATOR）。 */
    @PostMapping("/register")
    @OperationLog(module = "认证", title = "用户注册")
    public Result<Void> register(@RequestBody @Valid RegisterDTO dto) {
        authService.register(dto);
        return Result.success();
    }

    /** API-AUTH-001 登录（无需认证、无需按钮权限）。 */
    @PostMapping("/login")
    @OperationLog(module = "认证", title = "登录成功")
    public Result<LoginVO> login(@RequestBody @Valid LoginDTO dto) {
        return Result.success(authService.login(dto));
    }

    /** API-AUTH-002 刷新 access_token（无需认证）。 */
    @PostMapping("/refresh")
    public Result<LoginVO> refresh(@RequestBody @Valid RefreshDTO dto) {
        return Result.success(authService.refresh(dto));
    }

    /** API-AUTH-003 退出登录（需认证；幂等，重复调用同样返回 200）。 */
    @PostMapping("/logout")
    @OperationLog(module = "认证", title = "退出登录")
    public Result<Void> logout(@RequestBody(required = false) LogoutDTO dto) {
        authService.logout(dto == null ? new LogoutDTO() : dto);
        return Result.success();
    }

    /** API-AUTH-004 获取当前登录用户信息（需认证；驱动前端动态菜单与按钮权限）。 */
    @GetMapping("/info")
    public Result<AuthInfoVO> info() {
        return Result.success(authService.info());
    }
}
