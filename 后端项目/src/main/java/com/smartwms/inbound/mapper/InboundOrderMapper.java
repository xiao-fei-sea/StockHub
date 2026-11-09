package com.smartwms.inbound.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwms.entity.InboundOrder;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** inbound_order 数据访问。 */
public interface InboundOrderMapper extends BaseMapper<InboundOrder> {

    /**
     * 查询指定前缀的最新单号（生成 RK+日期+3位序号用，如 prefix=RK20261109）。
     * 并发重复由 uk_inbound_no 兜底 → 40904。
     */
    @Select("SELECT MAX(inbound_no) FROM inbound_order WHERE inbound_no LIKE CONCAT(#{prefix}, '%')")
    String selectLatestNo(@Param("prefix") String prefix);

    /**
     * 物理删除主表（单据不做逻辑删除，基线第 6 节；仅草稿/已驳回经 INB-004 校验后调用）。
     */
    @Delete("DELETE FROM inbound_order WHERE id = #{id}")
    int deletePhysically(@Param("id") Long id);
}
