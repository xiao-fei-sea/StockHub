package com.smartwms.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwms.entity.InventoryBatch;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** inventory_batch 数据访问。 */
public interface InventoryBatchMapper extends BaseMapper<InventoryBatch> {

    /**
     * 按唯一键（product_id+warehouse_id+batch_no）加锁查询：
     * 入库过账时先锁批次行，避免并发过账对同一批次重复建行（uk_batch 并发兜底）。
     * FOR UPDATE 需在事务内调用。
     */
    @Select("""
            SELECT * FROM inventory_batch
            WHERE product_id = #{productId} AND warehouse_id = #{warehouseId}
              AND batch_no = #{batchNo} AND deleted = 0
            FOR UPDATE
            """)
    InventoryBatch selectByKeyForUpdate(@Param("productId") Long productId,
                                        @Param("warehouseId") Long warehouseId,
                                        @Param("batchNo") String batchNo);
}
