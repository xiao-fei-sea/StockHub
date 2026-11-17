package com.smartwms.module.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwms.entity.Product;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** product 数据访问。 */
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 删除前置校验（API-PROD-005 规则1）：库存、库存批次、库存流水，
     * 以及未完结单据（status 未完成/未取消，即 NOT IN (3,5)）明细引用该商品的总行数，
     * 大于 0 禁止删除（40901，提示改用停用）。
     * 业务表实体尚未建模，先以原生 SQL 计数，后续模块建模后可替换。
     */
    @Select("""
            SELECT (SELECT COUNT(*) FROM inventory      WHERE product_id = #{id} AND deleted = 0)
                 + (SELECT COUNT(*) FROM inventory_batch WHERE product_id = #{id} AND deleted = 0)
                 + (SELECT COUNT(*) FROM stock_flow     WHERE product_id = #{id} AND deleted = 0)
                 + (SELECT COUNT(*) FROM inbound_order_item  i
                    JOIN inbound_order  o ON i.order_id = o.id
                    WHERE i.product_id = #{id} AND o.status NOT IN (3, 5))
                 + (SELECT COUNT(*) FROM outbound_order_item i
                    JOIN outbound_order o ON i.order_id = o.id
                    WHERE i.product_id = #{id} AND o.status NOT IN (3, 5))
                 + (SELECT COUNT(*) FROM stocktake_order_item i
                    JOIN stocktake_order o ON i.order_id = o.id
                    WHERE i.product_id = #{id} AND o.status NOT IN (3, 5))
                 + (SELECT COUNT(*) FROM transfer_order_item i
                    JOIN transfer_order o ON i.order_id = o.id
                    WHERE i.product_id = #{id} AND o.status NOT IN (3, 5))
            """)
    long countRefs(@Param("id") Long id);

    /**
     * 商品已有批次库存记录数（API-PROD-004 规则3）：
     * batchFlag/shelfLifeFlag 由 1 改 0 时，大于 0 禁止变更（40901，待确认口径取「存在批次记录即拦截」）。
     */
    @Select("SELECT COUNT(*) FROM inventory_batch WHERE product_id = #{id} AND deleted = 0")
    long countBatches(@Param("id") Long id);
}
