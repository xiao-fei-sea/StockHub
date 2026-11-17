package com.smartwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 商品信息表 product（全局资料，约 5000 SKU）。 */
@Data
@TableName("product")
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商品编码（全局唯一，P- 前缀建议） */
    private String productCode;
    private String productName;
    private Long categoryId;
    private String brand;
    /** 基本计量单位 */
    private String unit;
    private String spec;
    /** 单件重量（千克） */
    private BigDecimal weight;
    /** 单件体积（立方米） */
    private BigDecimal volume;
    /** 是否保质期管理：0 否 / 1 是 */
    private Integer shelfLifeFlag;
    /** 保质期天数（shelf_life_flag=1 时有效） */
    private Integer shelfLifeDays;
    /** 是否批次管理：0 否 / 1 是（默认 1；0 时入库批次号自动生成 DEFAULT） */
    private Integer batchFlag;
    /** 最低安全库存（预警下限） */
    private BigDecimal minStock;
    /** 最高库存上限（预警上限） */
    private BigDecimal maxStock;
    /** 参考采购价 */
    private BigDecimal price;
    private String imageUrl;
    /** 状态：0 停用 / 1 上架 */
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;
    private Integer deleted;
}
