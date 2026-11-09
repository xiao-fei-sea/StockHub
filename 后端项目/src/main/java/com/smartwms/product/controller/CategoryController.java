package com.smartwms.product.controller;

import com.smartwms.aspect.OperationLog;
import com.smartwms.product.dto.CategoryCreateDTO;
import com.smartwms.product.dto.CategoryUpdateDTO;
import com.smartwms.product.service.CategoryService;
import com.smartwms.product.vo.CategoryVO;
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
 * 商品分类接口 API-CAT-001~004（详见《商品管理接口文档》）。
 * 全部需 JWT 认证；树查询仅需登录，写操作按 sys:category:* 校验。
 */
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /** CAT-001 分类树查询（字面量路径优先于 /{id}；keyword 保留命中节点及祖先）。 */
    @GetMapping("/tree")
    public Result<List<CategoryVO>> tree(@RequestParam(required = false) Integer status,
                                         @RequestParam(required = false) String keyword) {
        return Result.success(categoryService.tree(status, keyword));
    }

    /** CAT-002 新增分类（code 全局唯一，level 服务端维护，最大 5 级）。 */
    @PostMapping
    @PreAuthorize("@perm.has('sys:category:add')")
    @OperationLog(module = "商品分类", title = "新增分类")
    public Result<Long> create(@RequestBody @Valid CategoryCreateDTO dto) {
        return Result.success(categoryService.create(dto));
    }

    /** CAT-003 修改分类（防环 40901，变更父级级联重算 level）。 */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('sys:category:edit')")
    @OperationLog(module = "商品分类", title = "修改分类")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid CategoryUpdateDTO dto) {
        categoryService.update(id, dto);
        return Result.success();
    }

    /** CAT-004 删除分类（存在下级分类/商品引用时 40901，逻辑删除）。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('sys:category:delete')")
    @OperationLog(module = "商品分类", title = "删除分类")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.success();
    }
}
