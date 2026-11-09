package com.smartwms.inbound.controller;

import com.smartwms.aspect.OperationLog;
import com.smartwms.inbound.dto.InboundCreateDTO;
import com.smartwms.inbound.dto.InboundPageQueryDTO;
import com.smartwms.inbound.dto.InboundUpdateDTO;
import com.smartwms.inbound.dto.OrderActionDTO;
import com.smartwms.inbound.service.InboundService;
import com.smartwms.inbound.vo.InboundOrderVO;
import com.smartwms.inbound.vo.InboundPostResultVO;
import com.smartwms.inventory.vo.StockFlowVO;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 入库管理接口 API-INB-001~011（详见《9. 入库管理接口文档》）。
 * 全部需 JWT 认证并叠加数据仓库权限（40302）；
 * 查询仅需登录；写操作按 sys:inbound:* 校验；乐观锁 version 不一致 40905。
 */
@RestController
@RequestMapping("/api/inbound-orders")
@RequiredArgsConstructor
public class InboundController {

    private final InboundService inboundService;

    /** INB-001 入库单分页查询（默认创建时间倒序）。 */
    @GetMapping
    public Result<PageResult<InboundOrderVO>> page(@Valid InboundPageQueryDTO dto) {
        return Result.success(inboundService.page(dto));
    }

    /** INB-002 新增入库单（RK 单号自动生成，初始草稿，返回含明细）。 */
    @PostMapping
    @PreAuthorize("@perm.has('sys:inbound:add')")
    @OperationLog(module = "入库管理", title = "新增入库单")
    public Result<InboundOrderVO> create(@RequestBody @Valid InboundCreateDTO dto) {
        return Result.success(inboundService.create(dto));
    }

    /** INB-003 入库单详情（含明细）。 */
    @GetMapping("/{id}")
    public Result<InboundOrderVO> detail(@PathVariable Long id) {
        return Result.success(inboundService.detail(id));
    }

    /** INB-003 修改入库单（仅草稿/已驳回，整体替换明细）。 */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('sys:inbound:edit')")
    @OperationLog(module = "入库管理", title = "修改入库单")
    public Result<InboundOrderVO> update(@PathVariable Long id, @RequestBody @Valid InboundUpdateDTO dto) {
        return Result.success(inboundService.update(id, dto));
    }

    /** INB-004 删除入库单（仅草稿/已驳回，物理删除）。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('sys:inbound:delete')")
    @OperationLog(module = "入库管理", title = "删除入库单")
    public Result<Void> delete(@PathVariable Long id) {
        inboundService.delete(id);
        return Result.success();
    }

    /** INB-005 提交审核（0→1）。 */
    @PostMapping("/{id}/submit")
    @PreAuthorize("@perm.has('sys:inbound:submit')")
    @OperationLog(module = "入库管理", title = "提交入库单")
    public Result<InboundOrderVO> submit(@PathVariable Long id, @RequestBody @Valid OrderActionDTO dto) {
        return Result.success(inboundService.submit(id, dto));
    }

    /** INB-006 审核通过（1→2）。 */
    @PostMapping("/{id}/audit")
    @PreAuthorize("@perm.has('sys:inbound:audit')")
    @OperationLog(module = "入库管理", title = "审核入库单")
    public Result<InboundOrderVO> audit(@PathVariable Long id, @RequestBody @Valid OrderActionDTO dto) {
        return Result.success(inboundService.audit(id, dto));
    }

    /** INB-007 驳回（1→4，reason 必填，存入 remark）。 */
    @PostMapping("/{id}/reject")
    @PreAuthorize("@perm.has('sys:inbound:audit')")
    @OperationLog(module = "入库管理", title = "驳回入库单")
    public Result<InboundOrderVO> reject(@PathVariable Long id, @RequestBody @Valid OrderActionDTO dto) {
        return Result.success(inboundService.reject(id, dto));
    }

    /** INB-008 过账（2→3，锁批次/库存行、加库存、生成流水，整体回滚）。 */
    @PostMapping("/{id}/post")
    @PreAuthorize("@perm.has('sys:inbound:post')")
    @OperationLog(module = "入库管理", title = "入库单过账")
    public Result<InboundPostResultVO> post(@PathVariable Long id, @RequestBody @Valid OrderActionDTO dto) {
        return Result.success(inboundService.post(id, dto));
    }

    /** INB-009 取消（0/1/4→5 终态）。 */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("@perm.has('sys:inbound:cancel')")
    @OperationLog(module = "入库管理", title = "取消入库单")
    public Result<InboundOrderVO> cancel(@PathVariable Long id, @RequestBody @Valid OrderActionDTO dto) {
        return Result.success(inboundService.cancel(id, dto));
    }

    /** INB-010 撤回（1/4→0）。 */
    @PostMapping("/{id}/withdraw")
    @PreAuthorize("@perm.has('sys:inbound:submit')")
    @OperationLog(module = "入库管理", title = "撤回入库单")
    public Result<InboundOrderVO> withdraw(@PathVariable Long id, @RequestBody @Valid OrderActionDTO dto) {
        return Result.success(inboundService.withdraw(id, dto));
    }

    /** INB-011 入库记录流水查询（未过账返回空列表）。 */
    @GetMapping("/{id}/flows")
    public Result<List<StockFlowVO>> flows(@PathVariable Long id) {
        return Result.success(inboundService.flows(id));
    }
}
