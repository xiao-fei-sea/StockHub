package com.smartwms.module.stocktake.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwms.entity.StocktakeOrder;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** stocktake_order 数据访问。 */
public interface StocktakeOrderMapper extends BaseMapper<StocktakeOrder> {

    /** 查询指定前缀的最新单号（生成 PD+日期+3位序号用）；并发由 uk_stocktake_no 兜底 40904。 */
    @Select("SELECT MAX(stocktake_no) FROM stocktake_order WHERE stocktake_no LIKE CONCAT(#{prefix}, '%')")
    String selectLatestNo(@Param("prefix") String prefix);

    /** 物理删除主表（单据不做逻辑删除，仅草稿经 STK-005 校验后调用）。 */
    @Delete("DELETE FROM stocktake_order WHERE id = #{id}")
    int deletePhysically(@Param("id") Long id);
}
