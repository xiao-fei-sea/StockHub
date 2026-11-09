package com.smartwms.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartwms.base.mapper.WarehouseMapper;
import com.smartwms.entity.SysDept;
import com.smartwms.entity.SysMenu;
import com.smartwms.entity.SysRole;
import com.smartwms.entity.SysUser;
import com.smartwms.entity.SysUserWarehouse;
import com.smartwms.entity.SysUserRole;
import com.smartwms.entity.Warehouse;
import com.smartwms.enums.ResultCode;
import com.smartwms.exception.BizException;
import com.smartwms.security.JwtUtil;
import com.smartwms.security.LoginUser;
import com.smartwms.security.LoginUserLoader;
import com.smartwms.system.dto.LoginDTO;
import com.smartwms.system.dto.LogoutDTO;
import com.smartwms.system.dto.RefreshDTO;
import com.smartwms.system.mapper.SysDeptMapper;
import com.smartwms.system.mapper.SysMenuMapper;
import com.smartwms.system.mapper.SysRoleMapper;
import com.smartwms.system.mapper.SysUserMapper;
import com.smartwms.system.mapper.SysUserRoleMapper;
import com.smartwms.system.mapper.SysUserWarehouseMapper;
import com.smartwms.system.service.AuthService;
import com.smartwms.system.vo.AuthInfoVO;
import com.smartwms.system.vo.LoginVO;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 认证服务实现。
 * 数据仓库不受限口径（基线第 5 节 / API 文档）：ROLE_ADMIN、ROLE_VIEWER 不受限，其余按 sys_user_warehouse。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService, LoginUserLoader {

    /** 数据仓库权限不受限的角色（待确认口径见 API 文档 14.2/14.4 注记） */
    private static final Set<String> UNRESTRICTED_ROLES = Set.of("ROLE_ADMIN", "ROLE_VIEWER");

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserWarehouseMapper userWarehouseMapper;
    private final SysDeptMapper deptMapper;
    private final WarehouseMapper warehouseMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO login(LoginDTO dto) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));
        // 40101：账号不存在与密码错误统一口径，不泄露账号是否存在
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.USER_PASSWORD_ERROR);
        }
        if (user.getStatus() == null || user.getStatus() == 0) {
            throw new BizException(ResultCode.USER_DISABLED);
        }

        user.setLoginDate(LocalDateTime.now());
        userMapper.updateById(user);

        LoginUser loginUser = buildLoginUser(user);
        return buildTokenVO(loginUser, true);
    }

    @Override
    public LoginVO refresh(RefreshDTO dto) {
        Long userId;
        try {
            userId = jwtUtil.parseRefreshToken(dto.getRefreshToken());
        } catch (JwtException | IllegalArgumentException e) {
            // refresh 过期/非法/类型冒用 → 401，前端跳登录页
            throw new BizException(ResultCode.UNAUTHORIZED, "refreshToken 已过期或非法");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null || (user.getStatus() != null && user.getStatus() == 0)) {
            throw new BizException(ResultCode.UNAUTHORIZED, "用户不存在或已停用");
        }
        return buildTokenVO(buildLoginUser(user), false);
    }

    @Override
    public void logout(LogoutDTO dto) {
        // JWT 无状态会话：服务端不维护会话表，令牌由前端丢弃；refreshToken 待确认是否引入黑名单
        // 幂等：重复登出同样返回成功
    }

    @Override
    public AuthInfoVO info() {
        LoginUser loginUser = requireLoginUser();
        SysUser user = userMapper.selectById(loginUser.getId());
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }

        // 角色（roleKey）
        List<SysRole> roles = selectEnabledRoles(user.getId());
        List<String> roleKeys = roles.stream().map(SysRole::getRoleKey).toList();

        // 权限标识 + 菜单树（启用菜单，perms 非空）
        List<SysMenu> menus = selectEnabledMenus(roles);
        List<String> permissions = menus.stream()
                .map(SysMenu::getPerms)
                .filter(p -> p != null && !p.isEmpty())
                .distinct()
                .sorted()
                .toList();

        // 数据仓库范围
        boolean unrestricted = roleKeys.stream().anyMatch(UNRESTRICTED_ROLES::contains);
        List<AuthInfoVO.WarehouseVO> warehouses = selectWarehouses(user.getId(), unrestricted);

        // 部门名
        String deptName = null;
        if (user.getDeptId() != null) {
            SysDept dept = deptMapper.selectById(user.getDeptId());
            deptName = dept == null ? null : dept.getDeptName();
        }

        AuthInfoVO.UserInfoVO userInfo = AuthInfoVO.UserInfoVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .deptId(user.getDeptId())
                .deptName(deptName)
                .status(user.getStatus())
                .loginDate(user.getLoginDate())
                .build();

        return AuthInfoVO.builder()
                .user(userInfo)
                .roles(roleKeys)
                .permissions(permissions)
                .menus(buildMenuTree(menus))
                .warehouses(warehouses)
                .build();
    }

    // ==================== LoginUserLoader ====================

    @Override
    public LoginUser loadLoginUser(Long userId, String username) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || (user.getStatus() != null && user.getStatus() == 0)) {
            return null;
        }
        // 账号被改后旧令牌作废：令牌内 username 与库内不一致视为失效
        if (username != null && !username.equals(user.getUsername())) {
            return null;
        }
        return buildLoginUser(user);
    }

    // ==================== 私有方法 ====================

    private LoginUser buildLoginUser(SysUser user) {
        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername());
        loginUser.setRealName(user.getRealName());

        List<SysRole> roles = selectEnabledRoles(user.getId());
        loginUser.setRoles(roles.stream().map(SysRole::getRoleKey).collect(Collectors.toSet()));

        List<SysMenu> menus = selectEnabledMenus(roles);
        loginUser.setPermissions(menus.stream()
                .map(SysMenu::getPerms)
                .filter(p -> p != null && !p.isEmpty())
                .collect(Collectors.toCollection(HashSet::new)));

        Set<String> roleKeys = loginUser.getRoles();
        boolean unrestricted = roleKeys.stream().anyMatch(UNRESTRICTED_ROLES::contains);
        loginUser.setWarehouseUnrestricted(unrestricted);
        if (!unrestricted) {
            loginUser.setWarehouseIds(userWarehouseMapper.selectList(
                            new LambdaQueryWrapper<SysUserWarehouse>()
                                    .eq(SysUserWarehouse::getUserId, user.getId()))
                    .stream().map(SysUserWarehouse::getWarehouseId)
                    .collect(Collectors.toCollection(HashSet::new)));
        }
        return loginUser;
    }

    private List<SysRole> selectEnabledRoles(Long userId) {
        List<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .in(SysRole::getId, roleIds)
                .eq(SysRole::getStatus, 1));
    }

    /** 启用且未删除的授权菜单（含 C/M/B；B 的 perms 进 permissions，不进菜单树）。 */
    private List<SysMenu> selectEnabledMenus(List<SysRole> roles) {
        if (roles.isEmpty()) {
            return List.of();
        }
        List<Long> roleIds = roles.stream().map(SysRole::getId).toList();
        List<Long> menuIds = menuMapper.selectMenuIdsByRoleIds(roleIds);
        if (menuIds.isEmpty()) {
            return List.of();
        }
        return menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .in(SysMenu::getId, menuIds)
                .eq(SysMenu::getStatus, 1));
    }

    private List<AuthInfoVO.WarehouseVO> selectWarehouses(Long userId, boolean unrestricted) {
        List<Warehouse> list;
        if (unrestricted) {
            list = warehouseMapper.selectList(new LambdaQueryWrapper<Warehouse>()
                    .eq(Warehouse::getStatus, 1)
                    .orderByAsc(Warehouse::getId));
        } else {
            List<Long> whIds = userWarehouseMapper.selectList(new LambdaQueryWrapper<SysUserWarehouse>()
                            .eq(SysUserWarehouse::getUserId, userId))
                    .stream().map(SysUserWarehouse::getWarehouseId).distinct().toList();
            if (whIds.isEmpty()) {
                return List.of();
            }
            list = warehouseMapper.selectList(new LambdaQueryWrapper<Warehouse>()
                    .in(Warehouse::getId, whIds)
                    .eq(Warehouse::getStatus, 1));
        }
        return list.stream()
                .map(w -> AuthInfoVO.WarehouseVO.builder()
                        .id(w.getId()).warehouseName(w.getWarehouseName()).build())
                .toList();
    }

    /** C/M 两级菜单树（sort 升序；B 按钮仅贡献 perms，不进树）。 */
    private List<AuthInfoVO.MenuNodeVO> buildMenuTree(List<SysMenu> menus) {
        List<SysMenu> routeMenus = menus.stream()
                .filter(m -> !"B".equals(m.getMenuType()))
                .sorted(Comparator.comparing(SysMenu::getSort, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(SysMenu::getId))
                .toList();

        Map<Long, List<SysMenu>> byParent = routeMenus.stream()
                .collect(Collectors.groupingBy(m -> m.getParentId() == null ? 0L : m.getParentId()));

        return buildChildren(byParent, 0L);
    }

    private List<AuthInfoVO.MenuNodeVO> buildChildren(Map<Long, List<SysMenu>> byParent, Long parentId) {
        List<SysMenu> children = byParent.getOrDefault(parentId, List.of());
        List<AuthInfoVO.MenuNodeVO> nodes = new ArrayList<>(children.size());
        for (SysMenu m : children) {
            nodes.add(AuthInfoVO.MenuNodeVO.builder()
                    .id(m.getId())
                    .parentId(parentId)
                    .menuName(m.getMenuName())
                    .menuType(m.getMenuType())
                    .path(m.getPath())
                    .component(m.getComponent())
                    .icon(m.getIcon())
                    .sort(m.getSort())
                    .visible(m.getVisible())
                    .children(buildChildren(byParent, m.getId()))
                    .build());
        }
        return nodes;
    }

    private LoginVO buildTokenVO(LoginUser loginUser, boolean includeUser) {
        LoginVO.LoginVOBuilder builder = LoginVO.builder()
                .accessToken(jwtUtil.generateAccessToken(loginUser))
                .refreshToken(jwtUtil.generateRefreshToken(loginUser))
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getAccessExpireSeconds());
        if (includeUser) {
            builder.user(LoginVO.TokenUserVO.builder()
                    .id(loginUser.getId())
                    .username(loginUser.getUsername())
                    .realName(loginUser.getRealName())
                    .status(1)
                    .roles(loginUser.getRoles().stream().sorted().toList())
                    .build());
        }
        return builder.build();
    }

    private LoginUser requireLoginUser() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return loginUser;
    }
}
