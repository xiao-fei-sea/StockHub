package com.smartwms.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwms.entity.Inventory;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/** inventory 数据访问。 */
public interface InventoryMapper extends BaseMapper<Inventory> {

    /**
     * 按唯一键行级加锁查询（过账并发控制：行锁 + 实体乐观锁 @Version 双保险）。
     * FOR UPDATE 需在事务内调用。
     */
    @Select("""
            SELECT * FROM inventory
            WHERE warehouse_id = #{warehouseId} AND location_id = #{locationId}
              AND product_id = #{productId} AND batch_id = #{batchId}
              AND deleted = 0
            FOR UPDATE
            """)
    Inventory selectForUpdate(@Param("warehouseId") Long warehouseId,
                              @Param("locationId") Long locationId,
                              @Param("productId") Long productId,
                              @Param("batchId") Long batchId);

    /**
     * 出库过账 FIFO 候选行：本仓库该商品可用库存（批次正常、qty>0），
     * 排序即基线 FIFO 口径：expiry_date IS NULL 排最后 → expiry_date ASC → production_date ASC → 批次 id ASC。
     * 行级 FOR UPDATE（出库/提交预检共用预检 SQL 见 sumAvailableQty）。
     */
    @Select("""
            SELECT i.* FROM inventory i
            JOIN inventory_batch b ON i.batch_id = b.id
            WHERE i.warehouse_id = #{warehouseId} AND i.product_id = #{productId}
              AND i.deleted = 0 AND b.deleted = 0
              AND b.batch_status = 1 AND i.qty > 0
            ORDER BY (b.expiry_date IS NULL), b.expiry_date ASC, b.production_date ASC, b.batch_id ASC
            FOR UPDATE
            """)
    List<Inventory> selectFifoCandidates(@Param("warehouseId") Long warehouseId,
                                         @Param("productId") Long productId);

    /**
     * 可用库存汇总（出库提交/审核预检用）：SUM(qty - lock_qty)，仅统计批次正常的库存行。
     * 预检口径 qty - lock_qty ≥ 需求汇总，不足返回 40902（提示性，最终以过账为准）。
     */
    @Select("""
            SELECT COALESCE(SUM(i.qty - i.lock_qty), 0) FROM inventory i
            JOIN inventory_batch b ON i.batch_id = b.id
            WHERE i.warehouse_id = #{warehouseId} AND i.product_id = #{productId}
              AND i.deleted = 0 AND b.deleted = 0 AND b.batch_status = 1
            """)
    BigDecimal sumAvailableQty(@Param("warehouseId") Long warehouseId,
                               @Param("productId") Long productId);

    /**
     * 调拨过账前按固定顺序预锁源仓库存行（文档 13.5 规则 4：
     * 行锁顺序 (warehouse_id, location_id, product_id, batch_id)，避免与其它过账死锁）。
     * 后续 FIFO 候选查询命中已锁行（同事务）不再阻塞；FOR UPDATE 需在事务内调用。
     */
    @Select("""
            <script>
            SELECT * FROM inventory
            WHERE warehouse_id = #{warehouseId} AND deleted = 0
              AND product_id IN
              <foreach collection="productIds" item="pid" open="(" separator="," close=")">#{pid}</foreach>
            ORDER BY location_id ASC, product_id ASC, batch_id ASC
            FOR UPDATE
            </script>
            """)
    List<Inventory> selectForUpdateByProducts(@Param("warehouseId") Long warehouseId,
                                              @Param("productIds") java.util.Collection<Long> productIds);
}
