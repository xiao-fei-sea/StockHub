package com.smartwms.product.controller;

import com.smartwms.aspect.OperationLog;
import com.smartwms.product.dto.ProductCreateDTO;
import com.smartwms.product.dto.ProductPageQueryDTO;
import com.smartwms.product.dto.ProductUpdateDTO;
import com.smartwms.product.service.ProductService;
import com.smartwms.product.vo.ProductOptionVO;
import com.smartwms.product.vo.ProductVO;
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
 * 商品管理接口 API-PROD-001~006（详见《商品管理接口文档》）。
 * 全部需 JWT 认证；查询类仅需登录（商品为全局资料，无数据仓库权限过滤），
 * 写操作按 sys:product:* 校验。
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /** PROD-001 商品分页查询（keyword 匹配编码/名称/品牌，按 id 降序）。 */
    @GetMapping
    public Result<PageResult<ProductVO>> page(@Valid ProductPageQueryDTO dto) {
        return Result.success(productService.page(dto));
    }

    /** PROD-006 商品下拉选项（字面量路径优先于 /{id}，默认仅上架，最多 100 条）。 */
    @GetMapping("/options")
    public Result<List<ProductOptionVO>> options(@RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) Long categoryId,
                                                 @RequestParam(required = false) Integer status,
                                                 @RequestParam(required = false) Integer batchFlag) {
        return Result.success(productService.options(keyword, categoryId, status, batchFlag));
    }

    /** PROD-003 商品详情（编辑表单回显，仅需登录）。 */
    @GetMapping("/{id}")
    public Result<ProductVO> detail(@PathVariable Long id) {
        return Result.success(productService.detail(id));
    }

    /** PROD-002 新增商品（productCode 全局唯一，batchFlag 缺省 1）。 */
    @PostMapping
    @PreAuthorize("@perm.has('sys:product:add')")
    @OperationLog(module = "商品管理", title = "新增商品")
    public Result<Long> create(@RequestBody @Valid ProductCreateDTO dto) {
        return Result.success(productService.create(dto));
    }

    /** PROD-004 修改商品（已有批次库存时关闭批次/保质期管理 40901）。 */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('sys:product:edit')")
    @OperationLog(module = "商品管理", title = "修改商品")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid ProductUpdateDTO dto) {
        productService.update(id, dto);
        return Result.success();
    }

    /** PROD-005 删除商品（存在库存/批次/流水/未完结单据引用时 40901）。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('sys:product:delete')")
    @OperationLog(module = "商品管理", title = "删除商品")
    public Result<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return Result.success();
    }
}
