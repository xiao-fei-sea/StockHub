package com.smartwms.product.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品详情/列表出参（API-PROD-001/002/003/004）。
 * createBy/updateBy 反查为用户名，查不到置 null。
 */
@Data
public class ProductVO {

    private Long id;
    private String productCode;
    private String productName;
    private Long categoryId;
    /** 所属分类名称（关联反查，查不到置 null） */
    private String categoryName;
    private String brand;
    private String unit;
    private String spec;
    private BigDecimal weight;
    private BigDecimal volume;
    private Integer shelfLifeFlag;
    private Integer shelfLifeDays;
    private Integer batchFlag;
    private BigDecimal minStock;
    private BigDecimal maxStock;
    private BigDecimal price;
    private String imageUrl;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String createBy;
    private String updateBy;
}
