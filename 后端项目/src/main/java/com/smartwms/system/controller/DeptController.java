package com.smartwms.system.controller;

import com.smartwms.aspect.OperationLog;
import com.smartwms.system.dto.DeptCreateDTO;
import com.smartwms.system.dto.DeptUpdateDTO;
import com.smartwms.system.service.DeptService;
import com.smartwms.system.vo.DeptTreeVO;
import com.smartwms.vo.Result;
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
 * 部门管理接口 API-DEPT-001~004（详见《用户管理接口文档》第 3 节）。
 * 全部需 JWT 认证；树查询无按钮权限，写操作按 sys:dept:* 校验。
 */
@RestController
@RequestMapping("/api/depts")
@RequiredArgsConstructor
public class DeptController {

    private final DeptService deptService;

    /** DEPT-001 部门树查询（可选 status 过滤）。 */
    @GetMapping("/tree")
    public Result<List<DeptTreeVO>> tree(@RequestParam(required = false) Integer status) {
        return Result.success(deptService.tree(status));
    }

    /** DEPT-002 新增部门（ancestors 服务端生成，同级名唯一）。 */
    @PostMapping
    @PreAuthorize("@perm.has('sys:dept:add')")
    @OperationLog(module = "用户管理", title = "新增部门")
    public Result<Long> create(@RequestBody @Valid DeptCreateDTO dto) {
        return Result.success(deptService.create(dto));
    }

    /** DEPT-003 修改部门（调父级时重建子树 ancestors，禁成环）。 */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('sys:dept:edit')")
    @OperationLog(module = "用户管理", title = "修改部门")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid DeptUpdateDTO dto) {
        deptService.update(id, dto);
        return Result.success();
    }

    /** DEPT-004 删除部门（有子级/有用户引用均拒绝）。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('sys:dept:delete')")
    @OperationLog(module = "用户管理", title = "删除部门")
    public Result<Void> delete(@PathVariable Long id) {
        deptService.delete(id);
        return Result.success();
    }
}
