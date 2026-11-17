package com.smartwms.module.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwms.entity.ProductCategory;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** product_category 数据访问。 */
public interface ProductCategoryMapper extends BaseMapper<ProductCategory> {

    /**
     * 删除前置校验：下级分类数量（API-CAT-004 规则1），大于 0 禁止删除（40901）。
     * product 实体尚未建模时即可用（product_category 自身表）。
     */
    @Select("SELECT COUNT(*) FROM product_category WHERE parent_id = #{id} AND deleted = 0")
    long countChildren(@Param("id") Long id);

    /** 删除前置校验：商品引用数量（API-CAT-004 规则2），大于 0 禁止删除（40901）。 */
    @Select("SELECT COUNT(*) FROM product WHERE category_id = #{id} AND deleted = 0")
    long countProducts(@Param("id") Long id);
}
