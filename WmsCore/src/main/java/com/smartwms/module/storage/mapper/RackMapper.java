package com.smartwms.module.storage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwms.entity.Rack;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** rack 数据访问。 */
public interface RackMapper extends BaseMapper<Rack> {

    /**
     * 删除前置校验：货架下级货位数量（API-RACK-004 规则1），大于 0 禁止删除（40901）。
     * 相关业务表实体尚未建模，先以原生 SQL 计数，后续模块建模后可替换。
     */
    @Select("SELECT COUNT(*) FROM location WHERE rack_id = #{id} AND deleted = 0")
    long countLocations(@Param("id") Long id);

    /**
     * 货架被库存引用的行数（API-RACK-004 规则2，间接引用）：
     * 库存经货位反查货架，大于 0 时禁止删除（40901）。
     */
    @Select("""
            SELECT COUNT(*) FROM inventory i
            JOIN location l ON i.location_id = l.id
            WHERE l.rack_id = #{id} AND l.deleted = 0 AND i.deleted = 0
            """)
    long countInventory(@Param("id") Long id);
}
