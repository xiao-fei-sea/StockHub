package com.smartwms.outbound.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwms.entity.OutboundOrder;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** outbound_order 数据访问。 */
public interface OutboundOrderMapper extends BaseMapper<OutboundOrder> {

    /**
     * 查询指定前缀的最新单号（生成 CK+日期+3位序号用，如 prefix=CK20261109）。
     * 并发重复由 uk_outbound_no 兜底 → 40904。
     */
    @Select("SELECT MAX(outbound_no) FROM outbound_order WHERE outbound_no LIKE CONCAT(#{prefix}, '%')")
    String selectLatestNo(@Param("prefix") String prefix);

    /**
     * 物理删除主表（单据不做逻辑删除，基线第 6 节；仅草稿经 OUT-005 校验后调用）。
     */
    @Delete("DELETE FROM outbound_order WHERE id = #{id}")
    int deletePhysically(@Param("id") Long id);
}
