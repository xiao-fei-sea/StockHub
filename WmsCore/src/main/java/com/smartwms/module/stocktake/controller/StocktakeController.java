package com.smartwms.module.stocktake.controller;

import com.smartwms.framework.aspect.OperationLog;
import com.smartwms.module.inbound.dto.OrderActionDTO;
import com.smartwms.module.stocktake.dto.StocktakeCreateDTO;
import com.smartwms.module.stocktake.dto.StocktakeInputDTO;
import com.smartwms.module.stocktake.dto.StocktakePageQueryDTO;
import com.smartwms.module.stocktake.service.StocktakeService;
import com.smartwms.module.stocktake.vo.StocktakeCreateVO;
import com.smartwms.module.stocktake.vo.StocktakeOrderVO;
import com.smartwms.module.stocktake.vo.StocktakePostResultVO;
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
 * 库存盘点接口 API-STK-001~011（详见《12. 库存盘点接口文档》）。
 * 全部需 JWT 认证 + sys:stocktake:* 权限，叠加数据仓库权限（40302）；
 * 动作方法为 PUT；差异须经审核授权后方可过账（状态机 40901），并发冲突 40907。
 */
@RestController
@RequestMapping("/api/stocktakes")
@RequiredArgsConstructor
public class StocktakeController {

    private final StocktakeService stocktakeService;

    /** STK-001 盘点单分页查询。 */
    @GetMapping
    @PreAuthorize("@perm.has('sys:stocktake:list')")
    public Result<PageResult<StocktakeOrderVO>> page(@Valid StocktakePageQueryDTO dto) {
        return Result.success(stocktakeService.page(dto));
    }

    /** STK-002 创建盘点单（按范围自动生成明细账面快照）。 */
    @PostMapping
    @PreAuthorize("@perm.has('sys:stocktake:add')")
    @OperationLog(module = "库存盘点", title = "创建盘点单")
    public Result<StocktakeCreateVO> create(@RequestBody @Valid StocktakeCreateDTO dto) {
        return Result.success(stocktakeService.create(dto));
    }

    /** STK-003 盘点单详情（含全部明细）。 */
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('sys:stocktake:list')")
    public Result<StocktakeOrderVO> detail(@PathVariable Long id) {
        return Result.success(stocktakeService.detail(id));
    }

    /** STK-004 录入/更新实盘数（仅 0/4，重算差异回草稿）。 */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('sys:stocktake:edit')")
    @OperationLog(module = "库存盘点", title = "录入实盘数")
    public Result<StocktakeOrderVO> input(@PathVariable Long id, @RequestBody @Valid StocktakeInputDTO dto) {
        return Result.success(stocktakeService.input(id, dto));
    }

    /** STK-005 删除盘点单（仅草稿，物理删除，version 可选）。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('sys:stocktake:delete')")
    @OperationLog(module = "库存盘点", title = "删除盘点单")
    public Result<Void> delete(@PathVariable Long id,
                               @RequestParam(required = false) Integer version) {
        stocktakeService.delete(id, version);
        return Result.success();
    }

    /** STK-006 提交待审核（0→1，实盘数须全部录入）。 */
    @PutMapping("/{id}/submit")
    @PreAuthorize("@perm.has('sys:stocktake:submit')")
    @OperationLog(module = "库存盘点", title = "提交盘点单")
    public Result<StocktakeOrderVO> submit(@PathVariable Long id, @RequestBody @Valid OrderActionDTO dto) {
        return Result.success(stocktakeService.submit(id, dto));
    }

    /** STK-007 审核通过（1→2，差异授权环节，不改库存）。 */
    @PutMapping("/{id}/audit")
    @PreAuthorize("@perm.has('sys:stocktake:audit')")
    @OperationLog(module = "库存盘点", title = "审核盘点单")
    public Result<StocktakeOrderVO> audit(@PathVariable Long id, @RequestBody @Valid OrderActionDTO dto) {
        return Result.success(stocktakeService.audit(id, dto));
    }

    /** STK-008 驳回（1→4，reason 必填，存 remark）。 */
    @PutMapping("/{id}/reject")
    @PreAuthorize("@perm.has('sys:stocktake:reject')")
    @OperationLog(module = "库存盘点", title = "驳回盘点单")
    public Result<StocktakeOrderVO> reject(@PathVariable Long id, @RequestBody @Valid OrderActionDTO dto) {
        return Result.success(stocktakeService.reject(id, dto));
    }

    /** STK-009 过账（2→3，按差异调库存，快照不一致 40907 整体回滚）。 */
    @PutMapping("/{id}/post")
    @PreAuthorize("@perm.has('sys:stocktake:post')")
    @OperationLog(module = "库存盘点", title = "盘点单过账")
    public Result<StocktakePostResultVO> post(@PathVariable Long id, @RequestBody @Valid OrderActionDTO dto) {
        return Result.success(stocktakeService.post(id, dto));
    }

    /** STK-010 取消（0/1/4→5 终态）。 */
    @PutMapping("/{id}/cancel")
    @PreAuthorize("@perm.has('sys:stocktake:cancel')")
    @OperationLog(module = "库存盘点", title = "取消盘点单")
    public Result<StocktakeOrderVO> cancel(@PathVariable Long id, @RequestBody @Valid OrderActionDTO dto) {
        return Result.success(stocktakeService.cancel(id, dto));
    }

    /** STK-011 撤回至草稿（1/4→0）。 */
    @PutMapping("/{id}/withdraw")
    @PreAuthorize("@perm.has('sys:stocktake:withdraw')")
    @OperationLog(module = "库存盘点", title = "撤回盘点单")
    public Result<StocktakeOrderVO> withdraw(@PathVariable Long id, @RequestBody @Valid OrderActionDTO dto) {
        return Result.success(stocktakeService.withdraw(id, dto));
    }
}
