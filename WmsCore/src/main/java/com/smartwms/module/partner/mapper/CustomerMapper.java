package com.smartwms.module.partner.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwms.entity.Customer;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** customer 数据访问。 */
public interface CustomerMapper extends BaseMapper<Customer> {

    /**
     * 删除前置校验（API-CUS-004 规则1）：出库单引用该客户的行数，
     * 大于 0 禁止删除（40901，提示改用停用）。
     * 业务表实体尚未建模，先以原生 SQL 计数，后续模块建模后可替换。
     */
    @Select("SELECT COUNT(*) FROM outbound_order WHERE customer_id = #{id} AND deleted = 0")
    long countRefs(@Param("id") Long id);
}
