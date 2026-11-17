package com.smartwms.module.product.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品分类树出参（API-CAT-001，字段与 OpenAPI ProductCategory 对齐）。
 * 树按 sort 升序、同级 id 升序；keyword 过滤时保留命中节点及其祖先。
 */
@Data
public class CategoryVO {

    private Long id;
    private Long parentId;
    private String categoryCode;
    private String categoryName;
    /** 分类层级（根为 1） */
    private Integer level;
    private Integer sort;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<CategoryVO> children;
}
