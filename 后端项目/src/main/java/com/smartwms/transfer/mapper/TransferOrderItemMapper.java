package com.smartwms.transfer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwms.entity.TransferOrderItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/** transfer_order_item 数据访问。 */
public interface TransferOrderItemMapper extends BaseMapper<TransferOrderItem> {

    /** 删除单据的全部明细（修改单据整体替换明细时先物理删除，与主表同事务）。 */
    @Delete("DELETE FROM transfer_order_item WHERE order_id = #{orderId}")
    int deleteByOrderId(@Param("orderId") Long orderId);
}
