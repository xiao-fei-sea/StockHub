package com.smartwms.stocktake.service;

import com.smartwms.inbound.dto.OrderActionDTO;
import com.smartwms.stocktake.dto.StocktakeCreateDTO;
import com.smartwms.stocktake.dto.StocktakeInputDTO;
import com.smartwms.stocktake.dto.StocktakePageQueryDTO;
import com.smartwms.stocktake.vo.StocktakeCreateVO;
import com.smartwms.stocktake.vo.StocktakeOrderVO;
import com.smartwms.stocktake.vo.StocktakePostResultVO;
import com.smartwms.vo.PageResult;

/**
 * 库存盘点服务（API-STK-001~011）。
 * 状态机同基线（0→1→2→3；4 撤回；0/1/4 取消）；差异必须经审核授权后方可过账；
 * 三重幂等：状态机 40901 + 单号唯一 40904 + 乐观锁 40905；并发冲突 40907。
 * book_qty 采用创建时点快照口径（12.7 待确认项默认值）。
 */
public interface StocktakeService {

    /** STK-001 盘点单分页查询（默认创建时间倒序）。 */
    PageResult<StocktakeOrderVO> page(StocktakePageQueryDTO dto);

    /** STK-002 创建盘点单（按范围自动生成明细快照，无明细 40001）。 */
    StocktakeCreateVO create(StocktakeCreateDTO dto);

    /** STK-003 盘点单详情（含全部明细）。 */
    StocktakeOrderVO detail(Long id);

    /** STK-004 录入/更新实盘数（仅 0/4，重算差异与汇总，回草稿 0）。 */
    StocktakeOrderVO input(Long id, StocktakeInputDTO dto);

    /** STK-005 删除盘点单（仅 0，物理删除，version 可选 40905）。 */
    void delete(Long id, Integer version);

    /** STK-006 提交（0→1，实盘数须全部录入 40001）。 */
    StocktakeOrderVO submit(Long id, OrderActionDTO dto);

    /** STK-007 审核（1→2，差异授权，不改库存）。 */
    StocktakeOrderVO audit(Long id, OrderActionDTO dto);

    /** STK-008 驳回（1→4，reason 必填 40001，存 remark）。 */
    StocktakeOrderVO reject(Long id, OrderActionDTO dto);

    /** STK-009 过账（2→3，按差异调库存写盘盈盘亏流水；冲突 40907）。 */
    StocktakePostResultVO post(Long id, OrderActionDTO dto);

    /** STK-010 取消（0/1/4→5 终态）。 */
    StocktakeOrderVO cancel(Long id, OrderActionDTO dto);

    /** STK-011 撤回（1/4→0）。 */
    StocktakeOrderVO withdraw(Long id, OrderActionDTO dto);
}
