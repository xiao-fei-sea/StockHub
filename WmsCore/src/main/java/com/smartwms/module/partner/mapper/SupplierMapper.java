package com.smartwms.module.partner.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwms.entity.Supplier;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** supplier 数据访问。 */
public interface SupplierMapper extends BaseMapper<Supplier> {

    /**
     * 删除前置校验（API-SUP-004 规则1）：入库单、库存批次引用该供应商的行数，
     * 大于 0 禁止删除（40901，提示改用停用）。
     * 业务表实体尚未建模，先以原生 SQL 计数，后续模块建模后可替换。
     */
    @Select("""
            SELECT (SELECT COUNT(*) FROM inbound_order   WHERE supplier_id = #{id} AND deleted = 0)
                 + (SELECT COUNT(*) FROM inventory_batch WHERE supplier_id = #{id} AND deleted = 0)
            """)
    long countRefs(@Param("id") Long id);
}
