package com.smartwms.module.system.controller;

import com.smartwms.framework.aspect.OperationLog;
import com.smartwms.module.system.dto.UserCreateDTO;
import com.smartwms.module.system.dto.UserPageQueryDTO;
import com.smartwms.module.system.dto.UserResetPasswordDTO;
import com.smartwms.module.system.dto.UserRoleAssignDTO;
import com.smartwms.module.system.dto.UserStatusDTO;
import com.smartwms.module.system.dto.UserUpdateDTO;
import com.smartwms.module.system.dto.UserWarehouseAssignDTO;
import com.smartwms.module.system.service.UserService;
import com.smartwms.module.system.vo.UserDetailVO;
import com.smartwms.module.system.vo.UserOptionVO;
import com.smartwms.module.system.vo.UserVO;
import com.smartwms.module.system.vo.UserWarehouseVO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理接口 API-USR-001~009、011（详见《用户管理接口文档》第 2 节）。
 * USR-010 位于 UserWarehouseController（/api/user-warehouses）。
 * 全部需 JWT 认证；写操作按钮权限与 sys_menu 一致。
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** USR-001 用户分页查询（菜单权限控制页面访问，无按钮权限）。 */
    @GetMapping
    public Result<PageResult<UserVO>> page(@Valid UserPageQueryDTO dto) {
        return Result.success(userService.page(dto));
    }

    /** USR-009 用户下拉选项（字面量路径优先于 /{id} 匹配）。 */
    @GetMapping("/options")
    public Result<java.util.List<UserOptionVO>> options(@RequestParam(required = false) String keyword) {
        return Result.success(userService.options(keyword));
    }

    /** USR-003 用户详情（编辑表单回显）。 */
    @GetMapping("/{id}")
    public Result<UserDetailVO> detail(@PathVariable Long id) {
        return Result.success(userService.detail(id));
    }

    /** USR-002 新增用户（含角色与授权仓库，单事务）。 */
    @PostMapping
    @PreAuthorize("@perm.has('sys:user:add')")
    @OperationLog(module = "用户管理", title = "新增用户")
    public Result<Long> create(@RequestBody @Valid UserCreateDTO dto) {
        return Result.success(userService.create(dto));
    }

    /** USR-004 修改用户基础信息（不含密码/角色/仓库）。 */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('sys:user:edit')")
    @OperationLog(module = "用户管理", title = "修改用户")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid UserUpdateDTO dto) {
        userService.update(id, dto);
        return Result.success();
    }

    /** USR-005 删除用户（逻辑删除并清理关联；admin/自身拒绝）。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('sys:user:delete')")
    @OperationLog(module = "用户管理", title = "删除用户")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.success();
    }

    /** USR-006 启用/停用用户。 */
    @PutMapping("/{id}/status")
    @PreAuthorize("@perm.has('sys:user:status')")
    @OperationLog(module = "用户管理", title = "变更用户状态")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody @Valid UserStatusDTO dto) {
        userService.updateStatus(id, dto);
        return Result.success();
    }

    /** USR-007 重置用户密码（BCrypt 覆盖，不回显）。 */
    @PutMapping("/{id}/password")
    @PreAuthorize("@perm.has('sys:user:reset')")
    @OperationLog(module = "用户管理", title = "重置密码")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody @Valid UserResetPasswordDTO dto) {
        userService.resetPassword(id, dto);
        return Result.success();
    }

    /** USR-008 分配用户角色（整体替换 sys_user_role）。 */
    @PutMapping("/{id}/roles")
    @PreAuthorize("@perm.has('sys:user:role')")
    @OperationLog(module = "用户管理", title = "分配角色")
    public Result<Void> assignRoles(@PathVariable Long id, @RequestBody @Valid UserRoleAssignDTO dto) {
        userService.assignRoles(id, dto);
        return Result.success();
    }

    /** USR-011 分配用户仓库数据权限（整体替换 sys_user_warehouse）。 */
    @PutMapping("/{id}/warehouses")
    @PreAuthorize("@perm.has('sys:user:assignWarehouse')")
    @OperationLog(module = "用户管理", title = "分配数据仓库")
    public Result<Void> assignWarehouses(@PathVariable Long id, @RequestBody @Valid UserWarehouseAssignDTO dto) {
        userService.assignWarehouses(id, dto);
        return Result.success();
    }
}
