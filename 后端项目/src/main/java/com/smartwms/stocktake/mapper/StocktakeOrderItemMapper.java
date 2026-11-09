package com.smartwms.stocktake.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwms.entity.StocktakeOrderItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/** stocktake_order_item 数据访问。 */
public interface StocktakeOrderItemMapper extends BaseMapper<StocktakeOrderItem> {

    /** 删除单据的全部明细（与主表物理删除同事务）。 */
    @Delete("DELETE FROM stocktake_order_item WHERE order_id = #{orderId}")
    int deleteByOrderId(@Param("orderId") Long orderId);
}
