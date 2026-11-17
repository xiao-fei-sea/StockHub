package com.smartwms.module.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartwms.module.product.mapper.ProductCategoryMapper;
import com.smartwms.module.product.mapper.ProductMapper;
import com.smartwms.entity.Product;
import com.smartwms.entity.ProductCategory;
import com.smartwms.entity.SysUser;
import com.smartwms.common.enums.ResultCode;
import com.smartwms.common.BizException;
import com.smartwms.module.product.dto.ProductCreateDTO;
import com.smartwms.module.product.dto.ProductPageQueryDTO;
import com.smartwms.module.product.dto.ProductUpdateDTO;
import com.smartwms.module.product.service.ProductService;
import com.smartwms.module.product.vo.ProductOptionVO;
import com.smartwms.module.product.vo.ProductVO;
import com.smartwms.module.system.mapper.SysUserMapper;
import com.smartwms.common.PageResult;
import com.smartwms.common.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 商品管理服务实现（API-PROD-001~006）。
 * 商品为全局资料，无数据仓库权限过滤（接口文档权限说明）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    /** options 最多返回条数（PROD-006 规则2「待确认」采纳建议：限制 100 条）。 */
    private static final int OPTIONS_LIMIT = 100;

    private final ProductMapper productMapper;
    private final ProductCategoryMapper categoryMapper;
    private final SysUserMapper userMapper;

    // ==================== PROD-001 分页查询 ====================

    @Override
    public PageResult<ProductVO> page(ProductPageQueryDTO dto) {

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .eq(dto.getCategoryId() != null, Product::getCategoryId, dto.getCategoryId())
                .eq(dto.getStatus() != null, Product::getStatus, dto.getStatus())
                .eq(dto.getShelfLifeFlag() != null, Product::getShelfLifeFlag, dto.getShelfLifeFlag())
                .eq(dto.getBatchFlag() != null, Product::getBatchFlag, dto.getBatchFlag())
                .orderByDesc(Product::getId); // 默认按 id 降序（文档待确认项采纳）
        if (StringUtils.hasText(dto.getKeyword())) {
            String like = "%" + dto.getKeyword().trim() + "%";
            wrapper.and(w -> w.like(Product::getProductCode, like)
                    .or().like(Product::getProductName, like)
                    .or().like(Product::getBrand, like));
        }

        return PageUtils.query(productMapper, dto, wrapper, this::toVOList);
    }

    // ==================== PROD-002 新增 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ProductCreateDTO dto) {
        validateCommon(dto.getShelfLifeFlag(), dto.getShelfLifeDays(),
                dto.getMinStock(), dto.getMaxStock());
        requireCategoryExists(dto.getCategoryId());

        // productCode 全局唯一（uk_product_code 并发兜底 → 40904）
        if (productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .eq(Product::getProductCode, dto.getProductCode().trim())) > 0) {
            throw new BizException(ResultCode.CODE_EXISTS, "编码已存在");
        }

        Product p = new Product();
        p.setProductCode(dto.getProductCode().trim());
        p.setProductName(dto.getProductName().trim());
        p.setCategoryId(dto.getCategoryId());
        p.setBrand(emptyToNull(dto.getBrand()));
        p.setUnit(dto.getUnit().trim());
        p.setSpec(emptyToNull(dto.getSpec()));
        p.setWeight(dto.getWeight());
        p.setVolume(dto.getVolume());
        p.setShelfLifeFlag(dto.getShelfLifeFlag() == null ? 0 : dto.getShelfLifeFlag());
        p.setShelfLifeDays(dto.getShelfLifeFlag() == null || dto.getShelfLifeFlag() == 0
                ? null : dto.getShelfLifeDays());
        p.setBatchFlag(dto.getBatchFlag() == null ? 1 : dto.getBatchFlag()); // 缺省 1（基线口径）
        p.setMinStock(dto.getMinStock());
        p.setMaxStock(dto.getMaxStock());
        p.setPrice(dto.getPrice());
        p.setImageUrl(emptyToNull(dto.getImageUrl()));
        p.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        p.setRemark(emptyToNull(dto.getRemark()));
        p.setCreateBy(currentUserId());
        productMapper.insert(p);
        log.info("新增商品成功 productId={}, productCode={}, productName={}",
                p.getId(), p.getProductCode(), p.getProductName());
        return p.getId();
    }

    // ==================== PROD-003 详情 ====================

    @Override
    public ProductVO detail(Long id) {
        Product p = productMapper.selectById(id);
        if (p == null) {
            throw new BizException(ResultCode.NOT_FOUND, "资源不存在");
        }
        return toVOList(List.of(p)).get(0);
    }

    // ==================== PROD-004 修改 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ProductUpdateDTO dto) {
        Product p = productMapper.selectById(id);
        if (p == null) {
            throw new BizException(ResultCode.NOT_FOUND, "资源不存在");
        }
        validateCommon(dto.getShelfLifeFlag(), dto.getShelfLifeDays(),
                dto.getMinStock(), dto.getMaxStock());
        requireCategoryExists(dto.getCategoryId());

        // productCode 唯一（排除自身）
        if (productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .eq(Product::getProductCode, dto.getProductCode().trim())
                .ne(Product::getId, id)) > 0) {
            throw new BizException(ResultCode.CODE_EXISTS, "编码已存在");
        }

        int newShelfLifeFlag = dto.getShelfLifeFlag() == null ? p.getShelfLifeFlag() : dto.getShelfLifeFlag();
        int newBatchFlag = dto.getBatchFlag() == null ? p.getBatchFlag() : dto.getBatchFlag();

        // 规则3：由 1 改 0 且已有批次库存时禁止变更（40901）
        boolean disableBatch = p.getBatchFlag() != null && p.getBatchFlag() == 1 && newBatchFlag == 0;
        boolean disableShelfLife = p.getShelfLifeFlag() != null && p.getShelfLifeFlag() == 1
                && newShelfLifeFlag == 0;
        if (disableBatch || disableShelfLife) {
            if (productMapper.countBatches(id) > 0) {
                throw new BizException(ResultCode.ORDER_STATUS_INVALID,
                        "商品已存在批次库存，禁止关闭批次/保质期管理（请先清理批次）");
            }
        }

        p.setProductCode(dto.getProductCode().trim());
        p.setProductName(dto.getProductName().trim());
        p.setCategoryId(dto.getCategoryId());
        p.setBrand(emptyToNull(dto.getBrand()));
        p.setUnit(dto.getUnit().trim());
        p.setSpec(emptyToNull(dto.getSpec()));
        p.setWeight(dto.getWeight());
        p.setVolume(dto.getVolume());
        p.setShelfLifeFlag(newShelfLifeFlag);
        p.setShelfLifeDays(newShelfLifeFlag == 0 ? null : dto.getShelfLifeDays());
        p.setBatchFlag(newBatchFlag);
        p.setMinStock(dto.getMinStock());
        p.setMaxStock(dto.getMaxStock());
        p.setPrice(dto.getPrice());
        p.setImageUrl(emptyToNull(dto.getImageUrl()));
        if (dto.getStatus() != null) {
            p.setStatus(dto.getStatus());
        }
        p.setRemark(emptyToNull(dto.getRemark()));
        p.setUpdateBy(currentUserId());
        productMapper.updateById(p);
        log.info("修改商品成功 productId={}, productCode={}", id, p.getProductCode());
    }

    // ==================== PROD-005 删除 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Product p = productMapper.selectById(id);
        if (p == null) {
            throw new BizException(ResultCode.NOT_FOUND, "资源不存在");
        }
        if (productMapper.countRefs(id) > 0) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID,
                    "商品被库存/批次/流水/未完结单据引用，不允许删除（请改用停用）");
        }
        productMapper.deleteById(id); // 逻辑删除 deleted=1
        log.info("删除商品成功 productId={}, productCode={}", id, p.getProductCode());
    }

    // ==================== PROD-006 下拉选项 ====================

    @Override
    public List<ProductOptionVO> options(String keyword, Long categoryId, Integer status, Integer batchFlag) {
        int st = status == null ? 1 : status;
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, st)
                .eq(categoryId != null, Product::getCategoryId, categoryId)
                .eq(batchFlag != null, Product::getBatchFlag, batchFlag)
                .orderByDesc(Product::getId)
                .last("LIMIT " + OPTIONS_LIMIT);
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim() + "%";
            wrapper.and(w -> w.like(Product::getProductCode, like)
                    .or().like(Product::getProductName, like));
        }
        return productMapper.selectList(wrapper).stream()
                .map(p -> ProductOptionVO.builder()
                        .id(p.getId())
                        .productCode(p.getProductCode())
                        .productName(p.getProductName())
                        .unit(p.getUnit())
                        .spec(p.getSpec())
                        .batchFlag(p.getBatchFlag())
                        .shelfLifeFlag(p.getShelfLifeFlag())
                        .build())
                .toList();
    }

    // ==================== 私有方法 ====================

    /**
     * 新增/修改共用校验（PROD-002 规则3/5/6）：
     * shelfLifeFlag=1 时 shelfLifeDays 必填 >0；为 0 时忽略；minStock ≤ maxStock（均填写时）。
     */
    private void validateCommon(Integer shelfLifeFlag, Integer shelfLifeDays,
                                BigDecimal minStock, BigDecimal maxStock) {
        if (shelfLifeFlag != null && shelfLifeFlag == 1) {
            if (shelfLifeDays == null || shelfLifeDays <= 0) {
                throw new BizException(ResultCode.PARAM_INVALID,
                        "启用保质期管理时 shelfLifeDays 必须为正整数");
            }
        }
        if (minStock != null && maxStock != null && minStock.compareTo(maxStock) > 0) {
            throw new BizException(ResultCode.PARAM_INVALID, "minStock 不能大于 maxStock");
        }
    }

    /** categoryId 必填且分类存在未删除，否则 40001。 */
    private void requireCategoryExists(Long categoryId) {
        if (categoryMapper.selectById(categoryId) == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "categoryId 对应分类不存在");
        }
    }

    /** 商品列表转 VO：批量反查分类名与操作人用户名（查不到置 null）。 */
    private List<ProductVO> toVOList(List<Product> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Set<Long> categoryIds = new HashSet<>();
        Set<Long> operatorIds = new HashSet<>();
        list.forEach(p -> {
            if (p.getCategoryId() != null) categoryIds.add(p.getCategoryId());
            if (p.getCreateBy() != null) operatorIds.add(p.getCreateBy());
            if (p.getUpdateBy() != null) operatorIds.add(p.getUpdateBy());
        });
        Map<Long, String> categoryNames = categoryIds.isEmpty() ? Map.of()
                : categoryMapper.selectBatchIds(categoryIds).stream()
                .collect(Collectors.toMap(ProductCategory::getId, ProductCategory::getCategoryName, (x, y) -> x));
        Map<Long, String> operatorNames = operatorIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(operatorIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getUsername, (x, y) -> x));

        List<ProductVO> result = new ArrayList<>(list.size());
        for (Product p : list) {
            ProductVO vo = new ProductVO();
            vo.setId(p.getId());
            vo.setProductCode(p.getProductCode());
            vo.setProductName(p.getProductName());
            vo.setCategoryId(p.getCategoryId());
            vo.setCategoryName(p.getCategoryId() == null ? null : categoryNames.get(p.getCategoryId()));
            vo.setBrand(p.getBrand());
            vo.setUnit(p.getUnit());
            vo.setSpec(p.getSpec());
            vo.setWeight(p.getWeight());
            vo.setVolume(p.getVolume());
            vo.setShelfLifeFlag(p.getShelfLifeFlag());
            vo.setShelfLifeDays(p.getShelfLifeDays());
            vo.setBatchFlag(p.getBatchFlag());
            vo.setMinStock(p.getMinStock());
            vo.setMaxStock(p.getMaxStock());
            vo.setPrice(p.getPrice());
            vo.setImageUrl(p.getImageUrl());
            vo.setStatus(p.getStatus());
            vo.setRemark(p.getRemark());
            vo.setCreateTime(p.getCreateTime());
            vo.setUpdateTime(p.getUpdateTime());
            vo.setCreateBy(p.getCreateBy() == null ? null : operatorNames.get(p.getCreateBy()));
            vo.setUpdateBy(p.getUpdateBy() == null ? null : operatorNames.get(p.getUpdateBy()));
            result.add(vo);
        }
        return result;
    }

    /** 空白字符串转 null（可选字段统一口径）。 */
    private String emptyToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    /** 当前登录用户 id（操作人）。 */
    private Long currentUserId() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.smartwms.framework.security.LoginUser loginUser) {
            return loginUser.getId();
        }
        return null;
    }
}
