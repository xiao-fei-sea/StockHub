package com.smartwms.module.system.controller;

import com.smartwms.framework.aspect.OperationLog;
import com.smartwms.module.system.dto.MenuCreateDTO;
import com.smartwms.module.system.dto.MenuUpdateDTO;
import com.smartwms.module.system.service.MenuService;
import com.smartwms.module.system.vo.MenuTreeVO;
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

import java.util.List;

/**
 * 菜单权限管理接口 API-MENU-001~004（详见《角色权限接口文档》第 2 节）。
 * 全部需 JWT 认证；树查询与 /api/auth/info 菜单同源，无按钮权限。
 */
@RestController
@RequestMapping("/api/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    /** MENU-001 菜单树查询（目录/菜单/按钮全量，可按 menuType/status 过滤）。 */
    @GetMapping("/tree")
    public Result<List<MenuTreeVO>> tree(@RequestParam(required = false) String menuType,
                                         @RequestParam(required = false) Integer status) {
        return Result.success(menuService.tree(menuType, status));
    }

    /** MENU-002 新增菜单节点。 */
    @PostMapping
    @PreAuthorize("@perm.has('sys:menu:add')")
    @OperationLog(module = "角色权限", title = "新增菜单")
    public Result<Long> create(@RequestBody @Valid MenuCreateDTO dto) {
        return Result.success(menuService.create(dto));
    }

    /** MENU-003 修改菜单节点（含防环与同级名校验）。 */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('sys:menu:edit')")
    @OperationLog(module = "角色权限", title = "修改菜单")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid MenuUpdateDTO dto) {
        menuService.update(id, dto);
        return Result.success();
    }

    /** MENU-004 删除菜单节点（有下级拒绝；清理 sys_role_menu 关联）。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('sys:menu:delete')")
    @OperationLog(module = "角色权限", title = "删除菜单")
    public Result<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return Result.success();
    }
}
