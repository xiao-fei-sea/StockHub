package com.smartwms.partner.controller;

import com.smartwms.aspect.OperationLog;
import com.smartwms.partner.dto.SupplierCreateDTO;
import com.smartwms.partner.dto.SupplierPageQueryDTO;
import com.smartwms.partner.dto.SupplierUpdateDTO;
import com.smartwms.partner.service.SupplierService;
import com.smartwms.partner.vo.SupplierOptionVO;
import com.smartwms.partner.vo.SupplierVO;
import com.smartwms.vo.PageResult;
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
 * 供应商管理接口 API-SUP-001~005（详见《供应商客户接口文档》）。
 * 全部需 JWT 认证；查询类仅需登录（共享资料，无数据仓库权限过滤），
 * 写操作按 sys:supplier:* 校验。
 */
@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    /** SUP-001 供应商分页查询。 */
    @GetMapping
    public Result<PageResult<SupplierVO>> page(@Valid SupplierPageQueryDTO dto) {
        return Result.success(supplierService.page(dto));
    }

    /** SUP-005 供应商下拉选项（字面量路径优先于 /{id}，最多 200 条）。 */
    @GetMapping("/options")
    public Result<List<SupplierOptionVO>> options(@RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) Integer status) {
        return Result.success(supplierService.options(keyword, status));
    }

    /** SUP-002 新增供应商（编码全局唯一，电话/邮箱格式校验）。 */
    @PostMapping
    @PreAuthorize("@perm.has('sys:supplier:add')")
    @OperationLog(module = "供应商管理", title = "新增供应商")
    public Result<Long> create(@RequestBody @Valid SupplierCreateDTO dto) {
        return Result.success(supplierService.create(dto));
    }

    /** SUP-003 修改供应商（编码唯一排除自身）。 */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('sys:supplier:edit')")
    @OperationLog(module = "供应商管理", title = "修改供应商")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid SupplierUpdateDTO dto) {
        supplierService.update(id, dto);
        return Result.success();
    }

    /** SUP-004 删除供应商（存在入库单/批次引用时 40901，逻辑删除）。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('sys:supplier:delete')")
    @OperationLog(module = "供应商管理", title = "删除供应商")
    public Result<Void> delete(@PathVariable Long id) {
        supplierService.delete(id);
        return Result.success();
    }
}
