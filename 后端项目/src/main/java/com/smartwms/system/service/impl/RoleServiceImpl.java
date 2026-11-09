package com.smartwms.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.entity.SysMenu;
import com.smartwms.entity.SysRole;
import com.smartwms.entity.SysRoleMenu;
import com.smartwms.entity.SysUser;
import com.smartwms.entity.SysUserRole;
import com.smartwms.enums.ResultCode;
import com.smartwms.exception.BizException;
import com.smartwms.security.LoginUser;
import com.smartwms.system.dto.RoleCreateDTO;
import com.smartwms.system.dto.RoleMenuAssignDTO;
import com.smartwms.system.dto.RolePageQueryDTO;
import com.smartwms.system.dto.RoleUpdateDTO;
import com.smartwms.system.mapper.SysMenuMapper;
import com.smartwms.system.mapper.SysRoleMapper;
import com.smartwms.system.mapper.SysRoleMenuMapper;
import com.smartwms.system.mapper.SysUserMapper;
import com.smartwms.system.mapper.SysUserRoleMapper;
import com.smartwms.system.service.RoleService;
import com.smartwms.system.vo.RoleDetailVO;
import com.smartwms.system.vo.RoleVO;
import com.smartwms.vo.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色管理服务实现（API-ROLE-001~006）。
 * 内置角色保护口径（接口文档待确认项按文档建议取 40901）：
 * 初始 5 角色 roleKey 禁改、ROLE_ADMIN 禁停用/禁删/禁改权限。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    /** 内置初始角色标识（设计基线第 5 节初始 5 角色） */
    private static final Set<String> BUILTIN_ROLE_KEYS = Set.of(
            "ROLE_ADMIN", "ROLE_MANAGER", "ROLE_OPERATOR", "ROLE_STOCKTAKE", "ROLE_VIEWER");
    private static final String ADMIN_ROLE_KEY = "ROLE_ADMIN";

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysMenuMapper menuMapper;
    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;

    // ==================== ROLE-001 分页查询 ====================

    @Override
    public PageResult<RoleVO> page(RolePageQueryDTO dto) {
        int pageNum = dto.getPageNum() == null ? 1 : dto.getPageNum();
        int pageSize = dto.getPageSize() == null ? 20 : Math.min(dto.getPageSize(), 100);

        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                .eq(dto.getStatus() != null, SysRole::getStatus, dto.getStatus())
                .orderByAsc(SysRole::getRoleSort)
                .orderByAsc(SysRole::getId);
        if (StringUtils.hasText(dto.getKeyword())) {
            String like = "%" + dto.getKeyword().trim() + "%";
            wrapper.and(w -> w.like(SysRole::getRoleName, like).or().like(SysRole::getRoleKey, like));
        }

        Page<SysRole> page = roleMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page.getTotal(), pageNum, pageSize, toRoleVOList(page.getRecords()));
    }

    // ==================== ROLE-002 新增 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(RoleCreateDTO dto) {
        checkRoleKeyUnique(dto.getRoleKey(), null);
        List<Long> menuIds = distinctOrEmpty(dto.getMenuIds());
        checkMenusExist(menuIds);

        SysRole role = new SysRole();
        role.setRoleName(dto.getRoleName());
        role.setRoleKey(dto.getRoleKey());
        role.setRoleSort(dto.getRoleSort() == null ? 0 : dto.getRoleSort());
        role.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        role.setRemark(emptyToNull(dto.getRemark()));
        role.setCreateBy(currentUserId());
        roleMapper.insert(role);

        saveRoleMenus(role.getId(), menuIds);
        log.info("新增角色成功 roleId={}, roleKey={}, menuIds={}", role.getId(), role.getRoleKey(), menuIds);
        return role.getId();
    }

    // ==================== ROLE-003 修改 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, RoleUpdateDTO dto) {
        SysRole role = requireRole(id);
        if (!role.getRoleKey().equals(dto.getRoleKey()) && BUILTIN_ROLE_KEYS.contains(role.getRoleKey())) {
            // 待确认：文档建议内置角色 roleKey 禁改，此处按 40901 处理
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "内置角色标识不允许修改");
        }
        checkRoleKeyUnique(dto.getRoleKey(), id);
        if (ADMIN_ROLE_KEY.equals(role.getRoleKey()) && dto.getStatus() != null && dto.getStatus() == 0) {
            // 待确认：文档建议 ROLE_ADMIN 禁停用，此处按 40901 处理
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "不允许停用内置管理员角色");
        }

        SysRole update = new SysRole();
        update.setId(id);
        update.setRoleName(dto.getRoleName());
        update.setRoleKey(dto.getRoleKey());
        if (dto.getRoleSort() != null) update.setRoleSort(dto.getRoleSort());
        if (dto.getStatus() != null) update.setStatus(dto.getStatus());
        update.setRemark(emptyToNull(dto.getRemark()));
        update.setUpdateBy(currentUserId());
        roleMapper.updateById(update);
        log.info("修改角色成功 roleId={}, roleKey={}", id, dto.getRoleKey());
    }

    // ==================== ROLE-004 删除 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysRole role = requireRole(id);
        if (ADMIN_ROLE_KEY.equals(role.getRoleKey())) {
            // 待确认：文档建议 ROLE_ADMIN 禁删，此处按 40901 处理
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "不允许删除内置管理员角色");
        }
        Long userCount = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, id));
        if (userCount != null && userCount > 0) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "存在用户引用该角色，不允许删除");
        }
        roleMapper.deleteById(id); // 逻辑删除 deleted=1
        // 物理清理 sys_role_menu（关联表无 deleted 列，避免权限残留）
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
        log.info("删除角色成功 roleId={}, roleKey={}", id, role.getRoleKey());
    }

    // ==================== ROLE-005 详情 ====================

    @Override
    public RoleDetailVO detail(Long id) {
        SysRole role = requireRole(id);
        RoleDetailVO vo = new RoleDetailVO();
        vo.setId(role.getId());
        vo.setRoleName(role.getRoleName());
        vo.setRoleKey(role.getRoleKey());
        vo.setRoleSort(role.getRoleSort());
        vo.setStatus(role.getStatus());
        vo.setRemark(role.getRemark());
        vo.setCreateTime(role.getCreateTime());
        vo.setUpdateTime(role.getUpdateTime());
        vo.setMenuIds(roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>()
                        .eq(SysRoleMenu::getRoleId, id))
                .stream().map(SysRoleMenu::getMenuId).distinct().sorted().toList());
        return vo;
    }

    // ==================== ROLE-006 分配菜单权限 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(Long id, RoleMenuAssignDTO dto) {
        SysRole role = requireRole(id);
        if (ADMIN_ROLE_KEY.equals(role.getRoleKey())) {
            // 待确认：ROLE_ADMIN 基线口径为全部权限，文档建议返回 40901 或忽略，此处按 40901 处理
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "不允许修改内置管理员角色的权限");
        }
        List<Long> menuIds = distinctOrEmpty(dto.getMenuIds());
        checkMenusExist(menuIds);

        // 整体替换 sys_role_menu（先清空后重建），相同集合重复提交幂等
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
        saveRoleMenus(id, menuIds);
        log.info("分配角色菜单权限成功 roleId={}, roleKey={}, menuIds={}", id, role.getRoleKey(), menuIds);
    }

    // ==================== 私有方法 ====================

    /** 角色必须存在且未逻辑删除，否则 404。 */
    private SysRole requireRole(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException(ResultCode.NOT_FOUND, "资源不存在");
        }
        return role;
    }

    /** roleKey 全局唯一（excludeId 排除自身），重复返回 40904；并发由 uk_sys_role_key 兜底。 */
    private void checkRoleKeyUnique(String roleKey, Long excludeId) {
        Long count = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleKey, roleKey)
                .ne(excludeId != null, SysRole::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException(ResultCode.CODE_EXISTS, "编码已存在");
        }
    }

    /** 菜单 id 全部存在且未删除，否则 40001。 */
    private void checkMenusExist(List<Long> menuIds) {
        if (menuIds.isEmpty()) {
            return;
        }
        Long count = menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>().in(SysMenu::getId, menuIds));
        if (count == null || count != menuIds.size()) {
            throw new BizException(ResultCode.PARAM_INVALID, "menuIds 存在不存在的菜单");
        }
    }

    /** 批量写入 sys_role_menu（入参已去重，uk_role_menu 防重）。 */
    private void saveRoleMenus(Long roleId, List<Long> menuIds) {
        for (Long menuId : menuIds) {
            SysRoleMenu rm = new SysRoleMenu();
            rm.setRoleId(roleId);
            rm.setMenuId(menuId);
            roleMenuMapper.insert(rm);
        }
    }

    /** 角色列表转 VO：createBy/updateBy 反查用户名。 */
    private List<RoleVO> toRoleVOList(List<SysRole> roles) {
        if (roles.isEmpty()) {
            return List.of();
        }
        Set<Long> operatorIds = new HashSet<>();
        roles.forEach(r -> {
            if (r.getCreateBy() != null) operatorIds.add(r.getCreateBy());
            if (r.getUpdateBy() != null) operatorIds.add(r.getUpdateBy());
        });
        Map<Long, String> operatorNames = operatorIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(operatorIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getUsername, (a, b) -> a));

        List<RoleVO> list = new ArrayList<>(roles.size());
        for (SysRole r : roles) {
            RoleVO vo = new RoleVO();
            vo.setId(r.getId());
            vo.setRoleName(r.getRoleName());
            vo.setRoleKey(r.getRoleKey());
            vo.setRoleSort(r.getRoleSort());
            vo.setStatus(r.getStatus());
            vo.setRemark(r.getRemark());
            vo.setCreateTime(r.getCreateTime());
            vo.setUpdateTime(r.getUpdateTime());
            vo.setCreateBy(r.getCreateBy() == null ? null : operatorNames.get(r.getCreateBy()));
            vo.setUpdateBy(r.getUpdateBy() == null ? null : operatorNames.get(r.getUpdateBy()));
            list.add(vo);
        }
        return list;
    }

    /** 当前登录用户 id。 */
    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser.getId();
        }
        return null;
    }

    /** 空集合归一为 emptyList 并去重。 */
    private List<Long> distinctOrEmpty(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(ids));
    }

    /** 空串归一为 null。 */
    private String emptyToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
