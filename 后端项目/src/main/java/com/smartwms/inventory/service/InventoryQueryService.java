package com.smartwms.inventory.service;

import com.smartwms.inventory.dto.FlowPageQueryDTO;
import com.smartwms.inventory.dto.InventoryPageQueryDTO;
import com.smartwms.inventory.vo.FlowItemVO;
import com.smartwms.inventory.vo.InventoryItemVO;
import com.smartwms.inventory.vo.InventorySummaryVO;
import com.smartwms.vo.PageResult;

/**
 * 库存查询服务（API-INV-001~003，全部只读）。
 * 数据仓库权限在 SQL 层过滤（40302）；pageSize 上限 100（超出 40001）。
 */
public interface InventoryQueryService {

    /** INV-001 库存多条件分页查询（含子分类过滤、onlyPositive、关键字）。 */
    PageResult<InventoryItemVO> page(InventoryPageQueryDTO dto);

    /** INV-002 库存变动流水分页查询（flowType 中文标签、日期范围、关联单号模糊）。 */
    PageResult<FlowItemVO> flows(FlowPageQueryDTO dto);

    /** INV-003 库存汇总（groupBy=warehouse/category/product，默认 warehouse；固定附带一级分类汇总）。 */
    InventorySummaryVO summary(String groupBy, Long warehouseId);
}
