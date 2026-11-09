package com.smartwms.partner.controller;

import com.smartwms.aspect.OperationLog;
import com.smartwms.partner.dto.CustomerCreateDTO;
import com.smartwms.partner.dto.CustomerPageQueryDTO;
import com.smartwms.partner.dto.CustomerUpdateDTO;
import com.smartwms.partner.service.CustomerService;
import com.smartwms.partner.vo.CustomerOptionVO;
import com.smartwms.partner.vo.CustomerVO;
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
 * 客户管理接口 API-CUS-001~005（详见《供应商客户接口文档》）。
 * 全部需 JWT 认证；查询类仅需登录（共享资料，无数据仓库权限过滤），
 * 写操作按 sys:customer:* 校验。
 */
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    /** CUS-001 客户分页查询。 */
    @GetMapping
    public Result<PageResult<CustomerVO>> page(@Valid CustomerPageQueryDTO dto) {
        return Result.success(customerService.page(dto));
    }

    /** CUS-005 客户下拉选项（字面量路径优先于 /{id}，最多 200 条）。 */
    @GetMapping("/options")
    public Result<List<CustomerOptionVO>> options(@RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) Integer status) {
        return Result.success(customerService.options(keyword, status));
    }

    /** CUS-002 新增客户（编码全局唯一，电话/邮箱格式校验）。 */
    @PostMapping
    @PreAuthorize("@perm.has('sys:customer:add')")
    @OperationLog(module = "客户管理", title = "新增客户")
    public Result<Long> create(@RequestBody @Valid CustomerCreateDTO dto) {
        return Result.success(customerService.create(dto));
    }

    /** CUS-003 修改客户（编码唯一排除自身）。 */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('sys:customer:edit')")
    @OperationLog(module = "客户管理", title = "修改客户")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid CustomerUpdateDTO dto) {
        customerService.update(id, dto);
        return Result.success();
    }

    /** CUS-004 删除客户（存在出库单引用时 40901，逻辑删除）。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('sys:customer:delete')")
    @OperationLog(module = "客户管理", title = "删除客户")
    public Result<Void> delete(@PathVariable Long id) {
        customerService.delete(id);
        return Result.success();
    }
}
