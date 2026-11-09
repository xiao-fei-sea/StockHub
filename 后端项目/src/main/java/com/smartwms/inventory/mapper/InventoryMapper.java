package com.smartwms.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwms.entity.Inventory;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
