package com.smartwms.module.system.controller;

import com.smartwms.framework.aspect.OperationLog;
import com.smartwms.module.system.dto.RoleCreateDTO;
import com.smartwms.module.system.dto.RoleMenuAssignDTO;
import com.smartwms.module.system.dto.RolePageQueryDTO;
import com.smartwms.module.system.dto.RoleUpdateDTO;
import com.smartwms.module.system.service.RoleService;
import com.smartwms.module.system.vo.RoleDetailVO;
import com.smartwms.module.system.vo.RoleVO;
import com.smartwms.common.PageResult;
import com.smartwms.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 角色管理接口 API-ROLE-001~006（详见《角色权限接口文档》第 1 节）。
 * 全部需 JWT 认证；写操作按钮权限与 sys_menu 一致。
 */
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    /** ROLE-001 角色分页查询（无按钮权限，仅需登录）。 */
    @GetMapping
    public Result<PageResult<RoleVO>> page(@Valid RolePageQueryDTO dto) {
        return Result.success(roleService.page(dto));
    }

    /** ROLE-005 角色详情（含已授权 menuIds，供权限配置回显）。 */
    @GetMapping("/{id}")
    public Result<RoleDetailVO> detail(@PathVariable Long id) {
        return Result.success(roleService.detail(id));
    }

    /** ROLE-002 新增角色（可同时初始化菜单权限集合）。 */
    @PostMapping
    @PreAuthorize("@perm.has('sys:role:add')")
    @OperationLog(module = "角色权限", title = "新增角色")
    public Result<Long> create(@RequestBody @Valid RoleCreateDTO dto) {
        return Result.success(roleService.create(dto));
    }

    /** ROLE-003 修改角色基本信息（不含 menuIds）。 */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('sys:role:edit')")
    @OperationLog(module = "角色权限", title = "修改角色")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid RoleUpdateDTO dto) {
        roleService.update(id, dto);
        return Result.success();
    }

    /** ROLE-004 删除角色（逻辑删除并清理 sys_role_menu）。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('sys:role:delete')")
    @OperationLog(module = "角色权限", title = "删除角色")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return Result.success();
    }

    /** ROLE-006 分配角色菜单权限（整体替换 sys_role_menu）。 */
    @PutMapping("/{id}/menus")
    @PreAuthorize("@perm.has('sys:role:assign')")
    @OperationLog(module = "角色权限", title = "分配菜单权限")
    public Result<Void> assignMenus(@PathVariable Long id, @RequestBody @Valid RoleMenuAssignDTO dto) {
        roleService.assignMenus(id, dto);
        return Result.success();
    }
}
