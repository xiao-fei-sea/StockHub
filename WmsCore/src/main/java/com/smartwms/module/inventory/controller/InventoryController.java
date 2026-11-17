package com.smartwms.module.inventory.controller;

import com.smartwms.module.inventory.dto.FlowPageQueryDTO;
import com.smartwms.module.inventory.dto.InventoryPageQueryDTO;
import com.smartwms.module.inventory.service.InventoryQueryService;
import com.smartwms.module.inventory.vo.FlowItemVO;
import com.smartwms.module.inventory.vo.InventoryItemVO;
import com.smartwms.module.inventory.vo.InventorySummaryVO;
import com.smartwms.common.PageResult;
import com.smartwms.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 库存管理（查询）接口 API-INV-001~003（详见《11. 库存管理接口文档》）。
 * 全部为只读接口，需 JWT 认证 + sys:inventory:list 权限，
 * 并叠加数据仓库权限过滤（warehouseId 未传时按授权仓库集合过滤，越权 40302）。
 */
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryQueryService inventoryQueryService;

    /** INV-001 库存多条件分页查询（pageSize 上限 100，超出 40001）。 */
    @GetMapping
    @PreAuthorize("@perm.has('sys:inventory:list')")
    public Result<PageResult<InventoryItemVO>> page(@Valid InventoryPageQueryDTO dto) {
        return Result.success(inventoryQueryService.page(dto));
    }

    /** INV-002 库存变动流水分页查询（flowType 中文标签、direction IN/OUT）。 */
    @GetMapping("/flows")
    @PreAuthorize("@perm.has('sys:inventory:list')")
    public Result<PageResult<FlowItemVO>> flows(@Valid FlowPageQueryDTO dto) {
        return Result.success(inventoryQueryService.flows(dto));
    }

    /** INV-003 库存汇总（groupBy=warehouse/category/product，默认 warehouse，附一级分类汇总）。 */
    @GetMapping("/summary")
    @PreAuthorize("@perm.has('sys:inventory:list')")
    public Result<InventorySummaryVO> summary(
            @RequestParam(required = false) String groupBy,
            @RequestParam(required = false) Long warehouseId) {
        return Result.success(inventoryQueryService.summary(groupBy, warehouseId));
    }
}
