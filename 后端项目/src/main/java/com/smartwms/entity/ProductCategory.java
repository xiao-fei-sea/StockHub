package com.smartwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 商品分类表 product_category（树形结构，根节点 parent_id=0，level 由服务端维护）。 */
@Data
@TableName("product_category")
public class ProductCategory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 上级分类 id，根节点为 0 */
    private Long parentId;
    /** 分类编码（全局唯一，FL- 前缀建议） */
    private String categoryCode;
    private String categoryName;
    /** 分类层级（1 为一级，服务端按 parentId 维护） */
    private Integer level;
    private Integer sort;
    /** 状态：0 停用 / 1 正常 */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;
    private Integer deleted;
}
