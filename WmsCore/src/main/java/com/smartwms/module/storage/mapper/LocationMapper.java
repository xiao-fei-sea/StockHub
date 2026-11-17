package com.smartwms.module.storage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwms.entity.Location;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** location 数据访问。 */
public interface LocationMapper extends BaseMapper<Location> {

    /**
     * 货位上库存记录数（API-LOC-003/004 校验）：
     * 大于 0 时禁止删除、禁止变更层级、禁止置为禁用/停用（40901 / 40906）。
     * inventory 实体尚未建模，先以原生 SQL 计数，后续库存模块建模后可替换。
     */
    @Select("SELECT COUNT(*) FROM inventory WHERE location_id = #{id} AND deleted = 0")
    long countInventory(@Param("id") Long id);

    /**
     * 货位被单据明细引用的行数（API-LOC-004 规则2）：
     * 入库单明细目标货位、出库单明细出库货位，大于 0 时禁止删除（40901）。
     */
    @Select("""
            SELECT (SELECT COUNT(*) FROM inbound_order_item  WHERE location_id = #{id} AND deleted = 0)
                 + (SELECT COUNT(*) FROM outbound_order_item WHERE location_id = #{id} AND deleted = 0)
            """)
    long countOrderItemRefs(@Param("id") Long id);
}
