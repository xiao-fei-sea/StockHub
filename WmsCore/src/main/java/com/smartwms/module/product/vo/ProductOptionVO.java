package com.smartwms.module.product.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 商品下拉选项出参（API-PROD-006）：精简字段，不分页，
 * 默认仅正常上架商品，最多 100 条；含 batchFlag/shelfLifeFlag 供单据表单控制录入项。
 */
@Data
@Builder
public class ProductOptionVO {

    private Long id;
    private String productCode;
    private String productName;
    private String unit;
    private String spec;
    private Integer batchFlag;
    private Integer shelfLifeFlag;
}
