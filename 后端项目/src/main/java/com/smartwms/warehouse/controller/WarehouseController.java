package com.smartwms.warehouse.controller;

import com.smartwms.aspect.OperationLog;
import com.smartwms.vo.PageResult;
import com.smartwms.vo.Result;
import com.smartwms.warehouse.dto.WarehouseCreateDTO;
import com.smartwms.warehouse.dto.WarehousePageQueryDTO;
import com.smartwms.warehouse.dto.WarehouseUpdateDTO;
import com.smartwms.warehouse.service.WarehouseService;
import com.smartwms.warehouse.vo.WarehouseOptionVO;
import com.smartwms.warehouse.vo.WarehouseVO;
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
 * 仓库管理接口 API-WH-001~006（详见《仓库管理接口文档》）。
 * 全部需 JWT 认证；查询类无按钮权限（结果按数据仓库权限过滤），
 * 写操作按 sys:warehouse:* 校验。
 */
@RestController
@RequestMapping("/api/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    /** WH-001 仓库分页查询（菜单权限控制页面访问，无按钮权限）。 */
    @GetMapping
    public Result<PageResult<WarehouseVO>> page(@Valid WarehousePageQueryDTO dto) {
        return Result.success(warehouseService.page(dto));
    }

    /** WH-006 仓库下拉选项（字面量路径优先于 /{id} 匹配）。 */
    @GetMapping("/options")
    public Result<List<WarehouseOptionVO>> options(@RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) Integer status) {
        return Result.success(warehouseService.options(keyword, status));
    }

    /** WH-003 仓库详情（编辑表单回显），越权访问返回 40302。 */
    @GetMapping("/{id}")
    public Result<WarehouseVO> detail(@PathVariable Long id) {
        return Result.success(warehouseService.detail(id));
    }

    /** WH-002 新增仓库（编码唯一，status 默认 1、sort 默认 0）。 */
    @PostMapping
    @PreAuthorize("@perm.has('sys:warehouse:add')")
    @OperationLog(module = "仓库管理", title = "新增仓库")
    public Result<Long> create(@RequestBody @Valid WarehouseCreateDTO dto) {
        return Result.success(warehouseService.create(dto));
    }

    /** WH-004 修改仓库（编码可改但须唯一，排除自身）。 */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('sys:warehouse:edit')")
    @OperationLog(module = "仓库管理", title = "修改仓库")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid WarehouseUpdateDTO dto) {
        warehouseService.update(id, dto);
        return Result.success();
    }

    /** WH-005 删除仓库（逻辑删除；存在库区/库存/单据引用时 40901）。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('sys:warehouse:delete')")
    @OperationLog(module = "仓库管理", title = "删除仓库")
    public Result<Void> delete(@PathVariable Long id) {
        warehouseService.delete(id);
        return Result.success();
    }
}
