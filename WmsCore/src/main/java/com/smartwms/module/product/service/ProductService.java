package com.smartwms.module.product.service;

import com.smartwms.module.product.dto.ProductCreateDTO;
import com.smartwms.module.product.dto.ProductPageQueryDTO;
import com.smartwms.module.product.dto.ProductUpdateDTO;
import com.smartwms.module.product.vo.ProductOptionVO;
import com.smartwms.module.product.vo.ProductVO;
import com.smartwms.common.PageResult;

import java.util.List;

/** 商品管理服务（API-PROD-001~006）。 */
public interface ProductService {

    /** PROD-001 商品分页查询（keyword 匹配编码/名称/品牌，categoryId 精确匹配，按 id 降序）。 */
    PageResult<ProductVO> page(ProductPageQueryDTO dto);

    /** PROD-002 新增商品（productCode 全局唯一 40904，保质期/精度/min≤max 校验 40001），返回新商品 id。 */
    Long create(ProductCreateDTO dto);

    /** PROD-003 商品详情（仅需登录，不存在返回 404）。 */
    ProductVO detail(Long id);

    /** PROD-004 修改商品（校验同新增；已有批次库存时关闭批次/保质期管理 40901）。 */
    void update(Long id, ProductUpdateDTO dto);

    /** PROD-005 删除商品（存在库存/批次/流水/未完结单据引用时 40901，逻辑删除）。 */
    void delete(Long id);

    /** PROD-006 商品下拉选项（默认仅上架，不分页，最多 100 条）。 */
    List<ProductOptionVO> options(String keyword, Long categoryId, Integer status, Integer batchFlag);
}
