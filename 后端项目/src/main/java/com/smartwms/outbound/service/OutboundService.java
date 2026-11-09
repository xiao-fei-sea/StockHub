package com.smartwms.outbound.service;

import com.smartwms.outbound.dto.OutboundCreateDTO;
import com.smartwms.outbound.dto.OutboundPageQueryDTO;
import com.smartwms.outbound.dto.OutboundUpdateDTO;
import com.smartwms.inbound.dto.OrderActionDTO;
import com.smartwms.outbound.vo.OutboundOrderVO;
import com.smartwms.outbound.vo.OutboundPostResultVO;
import com.smartwms.vo.PageResult;

/**
 * 出库管理服务（API-OUT-001~011）。
 * 单据状态机（OrderStatus）：0草稿→1待审核→2已审核→3已完成；4已驳回（可撤回）；5已取消（终态）。
 * 与入库同构，差异：提交/审核做可用库存预检（40902）、过账按 FIFO 分配批次扣减库存。
 * 全部接口叠加数据仓库权限过滤（40302）；乐观锁 version 不一致返回 40905。
 */
public interface OutboundService {

    /** OUT-001 出库单分页查询（默认创建时间倒序）。 */
    PageResult<OutboundOrderVO> page(OutboundPageQueryDTO dto);

    /** OUT-002 新增出库单（CK 单号自动生成，初始草稿，不校验库存），返回含明细。 */
    OutboundOrderVO create(OutboundCreateDTO dto);

    /** OUT-003 出库单详情（含明细，40401 单据不存在）。 */
    OutboundOrderVO detail(Long id);

    /** OUT-004 修改出库单（仅草稿/已驳回 40901，驳回→草稿，整体替换明细，40905）。 */
    OutboundOrderVO update(Long id, OutboundUpdateDTO dto);

    /** OUT-005 删除出库单（仅草稿 0 40901，物理删除主表+明细，version 可选 40905）。 */
    void delete(Long id, Integer version);

    /** OUT-006 提交（0→1，完整性预检 40001 + 可用库存预检 40902）。 */
    OutboundOrderVO submit(Long id, OrderActionDTO dto);

    /** OUT-007 审核（1→2，记录审核人/时间 + 可用库存预检 40902）。 */
    OutboundOrderVO audit(Long id, OrderActionDTO dto);

    /** OUT-008 驳回（1→4，reason 必填 40001，存入 remark）。 */
    OutboundOrderVO reject(Long id, OrderActionDTO dto);

    /** OUT-009 过账（2→3，事务内 FIFO 分配批次、扣减库存、生成流水，返回 flows）。 */
    OutboundPostResultVO post(Long id, OrderActionDTO dto);

    /** OUT-010 取消（0/1/4→5 终态）。 */
    OutboundOrderVO cancel(Long id, OrderActionDTO dto);

    /** OUT-011 撤回（1/4→0）。 */
    OutboundOrderVO withdraw(Long id, OrderActionDTO dto);
}
