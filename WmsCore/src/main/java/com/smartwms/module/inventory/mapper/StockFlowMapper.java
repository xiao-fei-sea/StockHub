package com.smartwms.module.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwms.entity.StockFlow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** stock_flow 数据访问。 */
public interface StockFlowMapper extends BaseMapper<StockFlow> {

    /**
     * 查询指定前缀的最新流水号（生成 LS+日期+5位序号用，如 prefix=LS20261109）。
     * 并发重复由 uk_flow_no 兜底 → 40904。
     */
    @Select("SELECT MAX(flow_no) FROM stock_flow WHERE flow_no LIKE CONCAT(#{prefix}, '%')")
    String selectLatestNo(@Param("prefix") String prefix);
}
