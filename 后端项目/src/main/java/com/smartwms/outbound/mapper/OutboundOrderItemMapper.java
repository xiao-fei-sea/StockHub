package com.smartwms.outbound.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwms.entity.OutboundOrderItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/** outbound_order_item 数据访问。 */
public interface OutboundOrderItemMapper extends BaseMapper<OutboundOrderItem> {

    /** 删除单据的全部明细（修改单据整体替换明细时先物理删除，与主表同事务）。 */
    @Delete("DELETE FROM outbound_order_item WHERE order_id = #{orderId}")
    int deleteByOrderId(@Param("orderId") Long orderId);
}
