package com.smartwms.module.product.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 新增商品入参（API-PROD-002）。
 * productCode 全局唯一（40904）；shelfLifeFlag=1 时 shelfLifeDays 条件必填（服务层校验 40001）；
 * minStock ≤ maxStock（均填写时）；batchFlag 缺省 1。
 */
@Data
public class ProductCreateDTO {

    @NotBlank(message = "productCode 不能为空")
    @Size(max = 50, message = "productCode 长度不能超过 50 个字符")
    private String productCode;

    @NotBlank(message = "productName 不能为空")
    @Size(max = 200, message = "productName 长度不能超过 200 个字符")
    private String productName;

    @NotNull(message = "categoryId 不能为空")
    private Long categoryId;

    @Size(max = 100, message = "brand 长度不能超过 100 个字符")
    private String brand;

    @NotBlank(message = "unit 不能为空")
    @Size(max = 20, message = "unit 长度不能超过 20 个字符")
    private String unit;

    @Size(max = 100, message = "spec 长度不能超过 100 个字符")
    private String spec;

    /** 单件重量（kg），非负最多 3 位小数（DECIMAL(10,3)） */
    @PositiveOrZero(message = "weight 不能为负数")
    @Digits(integer = 7, fraction = 3, message = "weight 最多 3 位小数")
    private BigDecimal weight;

    /** 单件体积（m³），非负最多 3 位小数 */
    @PositiveOrZero(message = "volume 不能为负数")
    @Digits(integer = 7, fraction = 3, message = "volume 最多 3 位小数")
    private BigDecimal volume;

    /** 是否保质期管理：0 否 / 1 是，默认 0 */
    @Min(value = 0, message = "shelfLifeFlag 仅允许 0 或 1")
    @Max(value = 1, message = "shelfLifeFlag 仅允许 0 或 1")
    private Integer shelfLifeFlag = 0;

    /** 保质期天数；shelfLifeFlag=1 时必填且 >0（服务层校验） */
    @Min(value = 1, message = "shelfLifeDays 必须大于 0")
    private Integer shelfLifeDays;

    /** 是否批次管理：0/1，默认 1 */
    @Min(value = 0, message = "batchFlag 仅允许 0 或 1")
    @Max(value = 1, message = "batchFlag 仅允许 0 或 1")
    private Integer batchFlag = 1;

    /** 最低安全库存（预警下限），非负最多 3 位小数 */
    @PositiveOrZero(message = "minStock 不能为负数")
    @Digits(integer = 9, fraction = 3, message = "minStock 最多 3 位小数")
    private BigDecimal minStock;

    /** 最高库存上限（预警上限） */
    @PositiveOrZero(message = "maxStock 不能为负数")
    @Digits(integer = 9, fraction = 3, message = "maxStock 最多 3 位小数")
    private BigDecimal maxStock;

    /** 参考采购价，非负最多 2 位小数 */
    @PositiveOrZero(message = "price 不能为负数")
    @Digits(integer = 10, fraction = 2, message = "price 最多 2 位小数")
    private BigDecimal price;

    @Size(max = 255, message = "imageUrl 长度不能超过 255 个字符")
    private String imageUrl;

    /** 状态：0 停用 / 1 上架，默认 1 */
    @Min(value = 0, message = "status 仅允许 0 或 1")
    @Max(value = 1, message = "status 仅允许 0 或 1")
    private Integer status = 1;

    @Size(max = 500, message = "remark 长度不能超过 500 个字符")
    private String remark;
}
