package com.smartwms.module.inventory.dto;

import com.smartwms.common.PageQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 库存多条件分页查询入参（API-INV-001）。
 * warehouseId 未传时按数据权限仓库集合过滤（40302）；
 * pageSize 超过 100 截断为 100；categoryId 按商品维度关联且含子分类。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InventoryPageQueryDTO extends PageQueryDTO {

    /** 仓库 ID（强烈建议必传，未传时按数据权限范围合并查询） */
    private Long warehouseId;

    /** 商品分类 ID（含该分类下所有子分类） */
    private Long categoryId;

    private Long productId;

    private Long locationId;

    /** 批次号（模糊匹配） */
    private String batchNo;

    /** 关键字（商品编码/名称/货位编码） */
    private String keyword;

    /** 仅显示 qty > 0 的记录，默认 false */
    private Boolean onlyPositive;
}
