package com.smartwms.module.inbound.service;

import com.smartwms.module.inbound.dto.InboundCreateDTO;
import com.smartwms.module.inbound.dto.InboundPageQueryDTO;
import com.smartwms.module.inbound.dto.InboundUpdateDTO;
import com.smartwms.module.inbound.dto.OrderActionDTO;
import com.smartwms.module.inbound.vo.InboundOrderVO;
import com.smartwms.module.inbound.vo.InboundPostResultVO;
import com.smartwms.common.PageResult;

/**
 * 入库管理服务（API-INB-001~011）。
 * 单据状态机（OrderStatus）：0草稿→1待审核→2已审核→3已完成；4已驳回（可撤回）；5已取消（终态）。
 * 全部接口叠加数据仓库权限过滤（40302）；乐观锁 version 不一致返回 40905。
 */
public interface InboundService {

    /** INB-001 入库单分页查询（默认创建时间倒序）。 */
    PageResult<InboundOrderVO> page(InboundPageQueryDTO dto);

    /** INB-002 新增入库单（RK 单号自动生成，初始草稿，totalQty 汇总），返回含明细。 */
    InboundOrderVO create(InboundCreateDTO dto);

    /** INB-003 入库单详情（含明细，40401 单据不存在）。 */
    InboundOrderVO detail(Long id);

    /** INB-003 修改入库单（仅草稿/已驳回 40901，驳回→草稿，整体替换明细，40905）。 */
    InboundOrderVO update(Long id, InboundUpdateDTO dto);

    /** INB-004 删除入库单（仅草稿/已驳回，物理删除主表+明细，40901）。 */
    void delete(Long id);

    /** INB-005 提交（0→1，完整性预检 40001）。 */
    InboundOrderVO submit(Long id, OrderActionDTO dto);

    /** INB-006 审核（1→2，记录审核人/时间）。 */
    InboundOrderVO audit(Long id, OrderActionDTO dto);

    /** INB-007 驳回（1→4，reason 必填 40001，存入 remark）。 */
    InboundOrderVO reject(Long id, OrderActionDTO dto);

    /** INB-008 过账（2→3，事务内锁批次/库存行、加库存、生成流水，返回 flows）。 */
    InboundPostResultVO post(Long id, OrderActionDTO dto);

    /** INB-009 取消（0/1/4→5 终态）。 */
    InboundOrderVO cancel(Long id, OrderActionDTO dto);

    /** INB-010 撤回（1/4→0）。 */
    InboundOrderVO withdraw(Long id, OrderActionDTO dto);

    /** INB-011 入库记录流水查询（按单据查 stock_flow，未过账返回空列表）。 */
    java.util.List<com.smartwms.module.inventory.vo.StockFlowVO> flows(Long id);
}
