package com.smartwms.product.dto;

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
 * 修改商品入参（API-PROD-004）。
 * 校验同新增；batchFlag/shelfLifeFlag 由 1 改 0 且已有批次库存时 40901。
 */
@Data
public class ProductUpdateDTO {

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

    @PositiveOrZero(message = "weight 不能为负数")
    @Digits(integer = 7, fraction = 3, message = "weight 最多 3 位小数")
    private BigDecimal weight;

    @PositiveOrZero(message = "volume 不能为负数")
    @Digits(integer = 7, fraction = 3, message = "volume 最多 3 位小数")
    private BigDecimal volume;

    @Min(value = 0, message = "shelfLifeFlag 仅允许 0 或 1")
    @Max(value = 1, message = "shelfLifeFlag 仅允许 0 或 1")
    private Integer shelfLifeFlag;

    /** 保质期天数；shelfLifeFlag=1 时必填且 >0（服务层校验） */
    @Min(value = 1, message = "shelfLifeDays 必须大于 0")
    private Integer shelfLifeDays;

    @Min(value = 0, message = "batchFlag 仅允许 0 或 1")
    @Max(value = 1, message = "batchFlag 仅允许 0 或 1")
    private Integer batchFlag;

    @PositiveOrZero(message = "minStock 不能为负数")
    @Digits(integer = 9, fraction = 3, message = "minStock 最多 3 位小数")
    private BigDecimal minStock;

    @PositiveOrZero(message = "maxStock 不能为负数")
    @Digits(integer = 9, fraction = 3, message = "maxStock 最多 3 位小数")
    private BigDecimal maxStock;

    @PositiveOrZero(message = "price 不能为负数")
    @Digits(integer = 10, fraction = 2, message = "price 最多 2 位小数")
    private BigDecimal price;

    @Size(max = 255, message = "imageUrl 长度不能超过 255 个字符")
    private String imageUrl;

    /** 状态：0 停用 / 1 上架 */
    @Min(value = 0, message = "status 仅允许 0 或 1")
    @Max(value = 1, message = "status 仅允许 0 或 1")
    private Integer status;

    @Size(max = 500, message = "remark 长度不能超过 500 个字符")
    private String remark;
}
