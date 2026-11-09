package com.smartwms.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwms.entity.StorageArea;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** storage_area 数据访问。 */
public interface StorageAreaMapper extends BaseMapper<StorageArea> {

    /**
     * 删除前置校验：库区下级货架数量（API-AREA-004 规则1），大于 0 禁止删除（40901）。
     * 相关业务表实体尚未建模，先以原生 SQL 计数，后续模块建模后可替换。
     */
    @Select("SELECT COUNT(*) FROM rack WHERE area_id = #{id} AND deleted = 0")
    long countRacks(@Param("id") Long id);

    /**
     * 库区被库存引用的行数（API-AREA-003 规则2 / AREA-004 规则2）：
     * 库存经货位反查库区，大于 0 时禁止变更所属仓库、禁止删除（40901）。
     */
    @Select("""
            SELECT COUNT(*) FROM inventory i
            JOIN location l ON i.location_id = l.id
            WHERE l.area_id = #{id} AND l.deleted = 0 AND i.deleted = 0
            """)
    long countInventory(@Param("id") Long id);
}
