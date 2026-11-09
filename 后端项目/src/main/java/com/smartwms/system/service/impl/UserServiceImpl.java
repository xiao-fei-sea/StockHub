package com.smartwms.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.base.mapper.WarehouseMapper;
import com.smartwms.entity.SysDept;
import com.smartwms.entity.SysRole;
import com.smartwms.entity.SysUser;
import com.smartwms.entity.SysUserWarehouse;
import com.smartwms.entity.SysUserRole;
import com.smartwms.entity.Warehouse;
import com.smartwms.enums.ResultCode;
import com.smartwms.exception.BizException;
import com.smartwms.security.LoginUser;
import com.smartwms.system.dto.UserCreateDTO;
import com.smartwms.system.dto.UserPageQueryDTO;
import com.smartwms.system.dto.UserResetPasswordDTO;
import com.smartwms.system.dto.UserRoleAssignDTO;
import com.smartwms.system.dto.UserStatusDTO;
import com.smartwms.system.dto.UserUpdateDTO;
import com.smartwms.system.dto.UserWarehouseAssignDTO;
import com.smartwms.system.mapper.SysDeptMapper;
import com.smartwms.system.mapper.SysRoleMapper;
import com.smartwms.system.mapper.SysUserMapper;
import com.smartwms.system.mapper.SysUserRoleMapper;
import com.smartwms.system.mapper.SysUserWarehouseMapper;
import com.smartwms.system.service.UserService;
import com.smartwms.system.vo.UserDetailVO;
import com.smartwms.system.vo.UserOptionVO;
import com.smartwms.system.vo.UserVO;
import com.smartwms.system.vo.UserWarehouseVO;
import com.smartwms.vo.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户管理服务实现（API-USR-001~011）。
 * 内置管理员判定口径：username = "admin"（基线第 5 节 admin 全权限、不可删除/停用）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    /** 内置管理员登录名 */
    private static final String BUILTIN_ADMIN = "admin";
    /** 不受限数据仓库口径与认证模块一致（ROLE_ADMIN、ROLE_VIEWER） */
    private static final Set<String> UNRESTRICTED_ROLES = Set.of("ROLE_ADMIN", "ROLE_VIEWER");
    private static final String ADMIN_ROLE_KEY = "ROLE_ADMIN";

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserWarehouseMapper userWarehouseMapper;
    private final SysDeptMapper deptMapper;
    private final WarehouseMapper warehouseMapper;
    private final PasswordEncoder passwordEncoder;

    // ==================== USR-001 分页查询 ====================

    @Override
    public PageResult<UserVO> page(UserPageQueryDTO dto) {
        int pageNum = dto.getPageNum() == null ? 1 : dto.getPageNum();
        int pageSize = dto.getPageSize() == null ? 20 : Math.min(dto.getPageSize(), 100);

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(dto.getStatus() != null, SysUser::getStatus, dto.getStatus())
                .eq(dto.getDeptId() != null, SysUser::getDeptId, dto.getDeptId())
                .orderByDesc(SysUser::getCreateTime)
                .orderByDesc(SysUser::getId);
        if (StringUtils.hasText(dto.getKeyword())) {
            String like = "%" + dto.getKeyword().trim() + "%";
            wrapper.and(w -> w.like(SysUser::getUsername, like)
                    .or().like(SysUser::getRealName, like)
                    .or().like(SysUser::getPhone, like));
        }

        Page<SysUser> page = userMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<UserVO> list = toUserVOList(page.getRecords());
        return PageResult.of(page.getTotal(), pageNum, pageSize, list);
    }

    // ==================== USR-002 新增 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(UserCreateDTO dto) {
        // username 唯一（并发由 uk_sys_user_username 兜底 → 40904）
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));
        if (exists != null && exists > 0) {
            throw new BizException(ResultCode.CODE_EXISTS, "编码已存在");
        }
        checkDeptExists(dto.getDeptId());
        List<Long> roleIds = distinctOrEmpty(dto.getRoleIds());
        List<Long> warehouseIds = distinctOrEmpty(dto.getWarehouseIds());
        checkRolesExist(roleIds);
        checkWarehousesExist(warehouseIds);

        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRealName(dto.getRealName());
        user.setPhone(emptyToNull(dto.getPhone()));
        user.setEmail(emptyToNull(dto.getEmail()));
        user.setDeptId(dto.getDeptId());
        user.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        user.setCreateBy(currentUserId());
        userMapper.insert(user);

        for (Long roleId : roleIds) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(user.getId());
            ur.setRoleId(roleId);
            userRoleMapper.insert(ur);
        }
        for (Long whId : warehouseIds) {
            SysUserWarehouse uw = new SysUserWarehouse();
            uw.setUserId(user.getId());
            uw.setWarehouseId(whId);
            userWarehouseMapper.insert(uw);
        }
        log.info("新增用户成功 username={}, userId={}, roleIds={}, warehouseIds={}",
                dto.getUsername(), user.getId(), roleIds, warehouseIds);
        return user.getId();
    }

    // ==================== USR-003 详情 ====================

    @Override
    public UserDetailVO detail(Long id) {
        SysUser user = requireUser(id);
        UserDetailVO vo = new UserDetailVO();
        fillBase(vo, user);
        vo.setRoleIds(userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, id))
                .stream().map(SysUserRole::getRoleId).sorted().toList());
        vo.setWarehouseIds(userWarehouseMapper.selectList(new LambdaQueryWrapper<SysUserWarehouse>()
                        .eq(SysUserWarehouse::getUserId, id))
                .stream().map(SysUserWarehouse::getWarehouseId).sorted().toList());
        return vo;
    }

    // ==================== USR-004 修改 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, UserUpdateDTO dto) {
        requireUser(id);
        // username 唯一：排除自身
        Long conflict = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername())
                .ne(SysUser::getId, id));
        if (conflict != null && conflict > 0) {
            throw new BizException(ResultCode.CODE_EXISTS, "编码已存在");
        }
        checkDeptExists(dto.getDeptId());

        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(dto.getUsername());
        user.setRealName(dto.getRealName());
        user.setPhone(emptyToNull(dto.getPhone()));
        user.setEmail(emptyToNull(dto.getEmail()));
        user.setDeptId(dto.getDeptId());
        if (dto.getStatus() != null) {
            user.setStatus(dto.getStatus());
        }
        user.setUpdateBy(currentUserId());
        userMapper.updateById(user);
        // 不触碰 password、角色、授权仓库（分别走 USR-007/008/011）
    }

    // ==================== USR-005 删除 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysUser user = requireUser(id);
        if (BUILTIN_ADMIN.equals(user.getUsername())) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "不允许删除内置管理员账号");
        }
        if (currentUserId() != null && currentUserId().equals(id)) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "不允许删除当前登录账号自身");
        }
        // 逻辑删除（deleted=1）+ 物理清理关联表（纯关系表无 deleted 列）
        userMapper.deleteById(id);
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        userWarehouseMapper.delete(new LambdaQueryWrapper<SysUserWarehouse>().eq(SysUserWarehouse::getUserId, id));
        log.info("删除用户成功 userId={}, username={}", id, user.getUsername());
    }

    // ==================== USR-006 启用/停用 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, UserStatusDTO dto) {
        SysUser user = requireUser(id);
        if (BUILTIN_ADMIN.equals(user.getUsername())) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "不允许停用内置管理员账号");
        }
        if (currentUserId() != null && currentUserId().equals(id)) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "不允许停用当前登录账号自身");
        }
        SysUser update = new SysUser();
        update.setId(id);
        update.setStatus(dto.getStatus());
        update.setUpdateBy(currentUserId());
        userMapper.updateById(update);
        log.info("变更用户状态 userId={}, username={}, status={}", id, user.getUsername(), dto.getStatus());
    }

    // ==================== USR-007 重置密码 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, UserResetPasswordDTO dto) {
        requireUser(id);
        SysUser update = new SysUser();
        update.setId(id);
        update.setPassword(passwordEncoder.encode(dto.getPassword()));
        update.setUpdateBy(currentUserId());
        userMapper.updateById(update);
        // 密码不回显；sys_oper_log.request_params 由切面经 SensitiveUtil 脱敏
        log.info("重置用户密码成功 userId={}", id);
    }

    // ==================== USR-008 分配角色 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long id, UserRoleAssignDTO dto) {
        SysUser user = requireUser(id);
        if (BUILTIN_ADMIN.equals(user.getUsername())) {
            // 待确认：接口文档 008 未登记 40901，本项按 40001 + 中文提示返回
            throw new BizException(ResultCode.PARAM_INVALID, "不允许修改内置管理员的角色");
        }
        List<Long> roleIds = distinctOrEmpty(dto.getRoleIds());
        checkRolesExist(roleIds);

        if (!roleIds.isEmpty()) {
            List<String> roleKeys = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                            .in(SysRole::getId, roleIds))
                    .stream().map(SysRole::getRoleKey).toList();
            if (roleKeys.contains(ADMIN_ROLE_KEY)) {
                throw new BizException(ResultCode.PARAM_INVALID, "不允许为普通用户授予 ROLE_ADMIN 角色");
            }
        }

        // 整体替换 sys_user_role（先清空后重建），单事务
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        for (Long roleId : roleIds) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(id);
            ur.setRoleId(roleId);
            userRoleMapper.insert(ur);
        }
        log.info("分配用户角色成功 userId={}, username={}, roleIds={}", id, user.getUsername(), roleIds);
    }

    // ==================== USR-009 下拉选项 ====================

    @Override
    public List<UserOptionVO> options(String keyword) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, 1)
                .orderByAsc(SysUser::getId);
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim() + "%";
            wrapper.and(w -> w.like(SysUser::getUsername, like).or().like(SysUser::getRealName, like));
        }
        return userMapper.selectList(wrapper).stream()
                .map(u -> UserOptionVO.builder()
                        .id(u.getId())
                        .username(u.getUsername())
                        .realName(u.getRealName())
                        .build())
                .toList();
    }

    // ==================== USR-010 查询用户仓库数据权限 ====================

    @Override
    public UserWarehouseVO getUserWarehouses(Long userId) {
        SysUser user = requireUser(userId);
        List<Warehouse> warehouses;
        if (hasUnrestrictedRole(userId)) {
            // admin/ROLE_VIEWER 全量口径（接口文档 USR-010 待确认项按“返回全量仓库”处理）
            warehouses = warehouseMapper.selectList(new LambdaQueryWrapper<Warehouse>()
                    .orderByAsc(Warehouse::getId));
        } else {
            List<Long> whIds = userWarehouseMapper.selectList(new LambdaQueryWrapper<SysUserWarehouse>()
                            .eq(SysUserWarehouse::getUserId, userId))
                    .stream().map(SysUserWarehouse::getWarehouseId).distinct().sorted().toList();
            if (whIds.isEmpty()) {
                return UserWarehouseVO.builder().userId(userId)
                        .warehouseIds(List.of()).warehouses(List.of()).build();
            }
            warehouses = warehouseMapper.selectList(new LambdaQueryWrapper<Warehouse>()
                    .in(Warehouse::getId, whIds)
                    .orderByAsc(Warehouse::getId));
        }
        return UserWarehouseVO.builder()
                .userId(userId)
                .warehouseIds(warehouses.stream().map(Warehouse::getId).toList())
                .warehouses(warehouses.stream()
                        .map(w -> UserWarehouseVO.WarehouseBriefVO.builder()
                                .id(w.getId())
                                .warehouseCode(w.getWarehouseCode())
                                .warehouseName(w.getWarehouseName())
                                .build())
                        .toList())
                .build();
    }

    // ==================== USR-011 分配仓库数据权限 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignWarehouses(Long id, UserWarehouseAssignDTO dto) {
        SysUser user = requireUser(id);
        List<Long> warehouseIds = distinctOrEmpty(dto.getWarehouseIds());
        checkWarehousesExist(warehouseIds);

        // 整体替换 sys_user_warehouse（先清空后重建），单事务
        userWarehouseMapper.delete(new LambdaQueryWrapper<SysUserWarehouse>()
                .eq(SysUserWarehouse::getUserId, id));
        for (Long whId : warehouseIds) {
            SysUserWarehouse uw = new SysUserWarehouse();
            uw.setUserId(id);
            uw.setWarehouseId(whId);
            userWarehouseMapper.insert(uw);
        }
        log.info("分配用户仓库权限成功 userId={}, username={}, warehouseIds={}",
                id, user.getUsername(), warehouseIds);
    }

    // ==================== 私有方法 ====================

    /** 用户必须存在且未逻辑删除，否则 404。 */
    private SysUser requireUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "资源不存在");
        }
        return user;
    }

    /** 部门存在性校验（deptId 为空视为不设置，跳过）。 */
    private void checkDeptExists(Long deptId) {
        if (deptId == null) {
            return;
        }
        if (deptMapper.selectById(deptId) == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "deptId 对应部门不存在");
        }
    }

    /** 角色 id 全部存在，否则 40001。 */
    private void checkRolesExist(List<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return;
        }
        Long count = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>().in(SysRole::getId, roleIds));
        if (count == null || count != roleIds.size()) {
            throw new BizException(ResultCode.PARAM_INVALID, "roleIds 存在不存在的角色");
        }
    }

    /** 仓库 id 全部存在，否则 40001。 */
    private void checkWarehousesExist(List<Long> warehouseIds) {
        if (warehouseIds.isEmpty()) {
            return;
        }
        Long count = warehouseMapper.selectCount(new LambdaQueryWrapper<Warehouse>()
                .in(Warehouse::getId, warehouseIds));
        if (count == null || count != warehouseIds.size()) {
            throw new BizException(ResultCode.PARAM_INVALID, "warehouseIds 存在不存在的仓库");
        }
    }

    /** 目标用户是否拥有不受限数据仓库角色（admin/ROLE_VIEWER）。 */
    private boolean hasUnrestrictedRole(Long userId) {
        List<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).toList();
        if (roleIds.isEmpty()) {
            return false;
        }
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>().in(SysRole::getId, roleIds))
                .stream().map(SysRole::getRoleKey).anyMatch(UNRESTRICTED_ROLES::contains);
    }

    /** Entity 列表转 VO 列表：补 deptName 与 createBy/updateBy 用户名。 */
    private List<UserVO> toUserVOList(List<SysUser> users) {
        if (users.isEmpty()) {
            return List.of();
        }
        // 部门名批量反查
        Set<Long> deptIds = users.stream().map(SysUser::getDeptId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> deptNames = deptIds.isEmpty() ? Map.of()
                : deptMapper.selectBatchIds(deptIds).stream()
                .collect(Collectors.toMap(SysDept::getId, SysDept::getDeptName, (a, b) -> a));

        // 创建人/更新人 id 批量反查用户名（已删除用户查不到置 null）
        Set<Long> operatorIds = new HashSet<>();
        users.forEach(u -> {
            if (u.getCreateBy() != null) operatorIds.add(u.getCreateBy());
            if (u.getUpdateBy() != null) operatorIds.add(u.getUpdateBy());
        });
        Map<Long, String> operatorNames = operatorIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(operatorIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getUsername, (a, b) -> a));

        List<UserVO> list = new ArrayList<>(users.size());
        for (SysUser u : users) {
            UserVO vo = new UserVO();
            fillBase(vo, u);
            vo.setDeptName(u.getDeptId() == null ? null : deptNames.get(u.getDeptId()));
            vo.setCreateBy(u.getCreateBy() == null ? null : operatorNames.get(u.getCreateBy()));
            vo.setUpdateBy(u.getUpdateBy() == null ? null : operatorNames.get(u.getUpdateBy()));
            list.add(vo);
        }
        return list;
    }

    /** 填充 UserVO 基础字段（不含 deptName/操作人，由调用方补）。 */
    private void fillBase(UserVO vo, SysUser u) {
        vo.setId(u.getId());
        vo.setUsername(u.getUsername());
        vo.setRealName(u.getRealName());
        vo.setPhone(u.getPhone());
        vo.setEmail(u.getEmail());
        vo.setDeptId(u.getDeptId());
        vo.setStatus(u.getStatus());
        vo.setLoginDate(u.getLoginDate());
        vo.setCreateTime(u.getCreateTime());
        vo.setUpdateTime(u.getUpdateTime());
    }

    /** 详情 VO 的 deptName 与操作人补齐。 */
    private void fillBase(UserDetailVO vo, SysUser u) {
        fillBase((UserVO) vo, u);
        if (u.getDeptId() != null) {
            SysDept dept = deptMapper.selectById(u.getDeptId());
            vo.setDeptName(dept == null ? null : dept.getDeptName());
        }
        if (u.getCreateBy() != null) {
            SysUser creator = userMapper.selectById(u.getCreateBy());
            vo.setCreateBy(creator == null ? null : creator.getUsername());
        }
        if (u.getUpdateBy() != null) {
            SysUser updater = userMapper.selectById(u.getUpdateBy());
            vo.setUpdateBy(updater == null ? null : updater.getUsername());
        }
    }

    /** 当前登录用户 id（未登录返回 null，用于 create_by/update_by）。 */
    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser.getId();
        }
        return null;
    }

    /** 空集合归一为 emptyList。 */
    private List<Long> distinctOrEmpty(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(ids));
    }

    /** 空串归一为 null（phone/email 可选清空）。 */
    private String emptyToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
