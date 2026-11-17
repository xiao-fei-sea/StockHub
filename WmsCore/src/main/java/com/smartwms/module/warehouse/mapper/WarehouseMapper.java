package com.smartwms.module.warehouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwms.entity.Warehouse;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** warehouse 数据访问。 */
public interface WarehouseMapper extends BaseMapper<Warehouse> {

    /**
     * 删除前置校验：统计仍引用该仓库的业务数据行数（API-WH-005）。
     * 覆盖库区、库存、入库/出库/盘点/调拨单；结果大于 0 时禁止删除（40901）。
     * 相关业务表实体尚未建模，先以原生 SQL 计数，后续模块建模后可替换为 Mapper 调用。
     */
    @Select("""
            SELECT (SELECT COUNT(*) FROM storage_area    WHERE warehouse_id = #{id} AND deleted = 0)
                 + (SELECT COUNT(*) FROM inventory       WHERE warehouse_id = #{id} AND deleted = 0)
                 + (SELECT COUNT(*) FROM inbound_order   WHERE warehouse_id = #{id} AND deleted = 0)
                 + (SELECT COUNT(*) FROM outbound_order  WHERE warehouse_id = #{id} AND deleted = 0)
                 + (SELECT COUNT(*) FROM stocktake_order WHERE warehouse_id = #{id} AND deleted = 0)
                 + (SELECT COUNT(*) FROM transfer_order  WHERE src_warehouse_id = #{id} AND deleted = 0)
                 + (SELECT COUNT(*) FROM transfer_order  WHERE dest_warehouse_id = #{id} AND deleted = 0)
            """)
    long countBusinessRefs(@Param("id") Long id);
}
