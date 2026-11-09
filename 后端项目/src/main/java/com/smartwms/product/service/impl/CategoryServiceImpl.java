package com.smartwms.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartwms.base.mapper.ProductCategoryMapper;
import com.smartwms.entity.ProductCategory;
import com.smartwms.enums.ResultCode;
import com.smartwms.exception.BizException;
import com.smartwms.product.dto.CategoryCreateDTO;
import com.smartwms.product.dto.CategoryUpdateDTO;
import com.smartwms.product.service.CategoryService;
import com.smartwms.product.vo.CategoryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 商品分类服务实现（API-CAT-001~004）。
 * 分类为全局树形资料（无数据仓库维度）；level 由服务端按 parentId 维护。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    /** 分类最大层级（文档「待确认」采纳建议：最多 5 级），超过返回 40001。 */
    private static final int MAX_LEVEL = 5;

    private final ProductCategoryMapper categoryMapper;

    // ==================== CAT-001 分类树查询 ====================

    @Override
    public List<CategoryVO> tree(Integer status, String keyword) {
        List<ProductCategory> all = categoryMapper.selectList(new LambdaQueryWrapper<ProductCategory>()
                .eq(status != null, ProductCategory::getStatus, status)
                .orderByAsc(ProductCategory::getSort)
                .orderByAsc(ProductCategory::getId));

        // keyword：保留命中节点及其祖先（文档「待确认」采纳级联保留）
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim() + "%";
            Set<Long> keep = new HashSet<>();
            Map<Long, ProductCategory> byId = new HashMap<>();
            all.forEach(c -> byId.put(c.getId(), c));
            for (ProductCategory c : all) {
                boolean hit = (c.getCategoryCode() != null && c.getCategoryCode().contains(keyword.trim()))
                        || (c.getCategoryName() != null && c.getCategoryName().contains(keyword.trim()));
                if (hit) {
                    // 本节点 + 逐级向上祖先
                    ProductCategory cur = c;
                    while (cur != null && keep.add(cur.getId())) {
                        cur = cur.getParentId() == null || cur.getParentId() == 0
                                ? null : byId.get(cur.getParentId());
                    }
                }
            }
            List<ProductCategory> filtered = new ArrayList<>(keep.size());
            for (ProductCategory c : all) {
                if (keep.contains(c.getId())) {
                    filtered.add(c);
                }
            }
            all = filtered;
        }

        // 转 VO 并按 parentId 组树（查询已按 sort/id 升序，插入顺序即为子节点顺序）
        Map<Long, CategoryVO> voById = new LinkedHashMap<>();
        List<CategoryVO> result = new ArrayList<>();
        for (ProductCategory c : all) {
            CategoryVO vo = new CategoryVO();
            vo.setId(c.getId());
            vo.setParentId(c.getParentId());
            vo.setCategoryCode(c.getCategoryCode());
            vo.setCategoryName(c.getCategoryName());
            vo.setLevel(c.getLevel());
            vo.setSort(c.getSort());
            vo.setStatus(c.getStatus());
            vo.setCreateTime(c.getCreateTime());
            vo.setUpdateTime(c.getUpdateTime());
            vo.setChildren(new ArrayList<>());
            voById.put(c.getId(), vo);
        }
        for (ProductCategory c : all) {
            CategoryVO vo = voById.get(c.getId());
            if (c.getParentId() == null || c.getParentId() == 0) {
                result.add(vo);
            } else {
                CategoryVO parent = voById.get(c.getParentId());
                if (parent != null) {
                    parent.getChildren().add(vo);
                } else {
                    result.add(vo); // 上级被 status 过滤/异常数据，挂根兜底
                }
            }
        }
        return result;
    }

    // ==================== CAT-002 新增 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(CategoryCreateDTO dto) {
        int level = resolveLevel(dto.getParentId()); // parentId 不存在 → 40001；超 5 级 → 40001

        // categoryCode 全局唯一（uk_category_code 并发兜底 → 40904）
        if (categoryMapper.selectCount(new LambdaQueryWrapper<ProductCategory>()
                .eq(ProductCategory::getCategoryCode, dto.getCategoryCode().trim())) > 0) {
            throw new BizException(ResultCode.CODE_EXISTS, "编码已存在");
        }
        // categoryName 同级（相同 parentId）唯一 → 40904
        if (categoryMapper.selectCount(new LambdaQueryWrapper<ProductCategory>()
                .eq(ProductCategory::getParentId, dto.getParentId())
                .eq(ProductCategory::getCategoryName, dto.getCategoryName().trim())) > 0) {
            throw new BizException(ResultCode.CODE_EXISTS, "同级分类名称已存在");
        }

        ProductCategory node = new ProductCategory();
        node.setParentId(dto.getParentId());
        node.setCategoryCode(dto.getCategoryCode().trim());
        node.setCategoryName(dto.getCategoryName().trim());
        node.setLevel(level);
        node.setSort(dto.getSort() == null ? 0 : dto.getSort());
        node.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        categoryMapper.insert(node);
        log.info("新增商品分类成功 categoryId={}, categoryCode={}, level={}",
                node.getId(), node.getCategoryCode(), level);
        return node.getId();
    }

    // ==================== CAT-003 修改 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, CategoryUpdateDTO dto) {
        ProductCategory node = categoryMapper.selectById(id);
        if (node == null) {
            throw new BizException(ResultCode.NOT_FOUND, "资源不存在");
        }

        List<ProductCategory> all = categoryMapper.selectList(null);
        Map<Long, ProductCategory> byId = new HashMap<>();
        all.forEach(c -> byId.put(c.getId(), c));

        // 防环：不允许移动到自身或其子孙（40901）
        if (id.equals(dto.getParentId())) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "不允许将分类移动到自身下");
        }
        if (dto.getParentId() != 0 && isDescendant(dto.getParentId(), id, byId)) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "不允许将分类移动到其子孙分类下");
        }

        boolean parentChanged = !dto.getParentId().equals(node.getParentId());
        int newLevel = node.getLevel();
        if (parentChanged) {
            // 层级变更需无下级分类（文档规则3采纳建议：商品可保留引用）
            if (categoryMapper.countChildren(id) > 0) {
                throw new BizException(ResultCode.ORDER_STATUS_INVALID,
                        "存在下级分类，禁止变更层级（可先改名或移动下级）");
            }
            newLevel = resolveLevel(dto.getParentId()); // 40001 / 超 5 级 40001
        }

        // categoryCode 全局唯一（排除自身）
        if (categoryMapper.selectCount(new LambdaQueryWrapper<ProductCategory>()
                .eq(ProductCategory::getCategoryCode, dto.getCategoryCode().trim())
                .ne(ProductCategory::getId, id)) > 0) {
            throw new BizException(ResultCode.CODE_EXISTS, "编码已存在");
        }
        // categoryName 同级唯一（排除自身）
        if (categoryMapper.selectCount(new LambdaQueryWrapper<ProductCategory>()
                .eq(ProductCategory::getParentId, dto.getParentId())
                .eq(ProductCategory::getCategoryName, dto.getCategoryName().trim())
                .ne(ProductCategory::getId, id)) > 0) {
            throw new BizException(ResultCode.CODE_EXISTS, "同级分类名称已存在");
        }

        node.setParentId(dto.getParentId());
        node.setCategoryCode(dto.getCategoryCode().trim());
        node.setCategoryName(dto.getCategoryName().trim());
        node.setLevel(newLevel);
        if (dto.getSort() != null) {
            node.setSort(dto.getSort());
        }
        if (dto.getStatus() != null) {
            node.setStatus(dto.getStatus());
        }
        categoryMapper.updateById(node);

        // 变更父级时级联重算子孙 level（本节点无下级时仅自身，保留实现以防后续放开限制）
        if (parentChanged) {
            cascadeLevel(node.getId(), newLevel, byId);
        }
        log.info("修改商品分类成功 categoryId={}, parentId={}, level={}",
                id, node.getParentId(), newLevel);
    }

    // ==================== CAT-004 删除 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ProductCategory node = categoryMapper.selectById(id);
        if (node == null) {
            throw new BizException(ResultCode.NOT_FOUND, "资源不存在");
        }
        if (categoryMapper.countChildren(id) > 0) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID,
                    "存在下级分类，禁止删除（自底向上删除）");
        }
        if (categoryMapper.countProducts(id) > 0) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID,
                    "分类下存在关联商品，禁止删除（请先迁移商品）");
        }
        categoryMapper.deleteById(id); // 逻辑删除 deleted=1
        log.info("删除商品分类成功 categoryId={}, categoryCode={}", id, node.getCategoryCode());
    }

    // ==================== 私有方法 ====================

    /**
     * 按 parentId 解析新分类层级：parentId=0 → 1；否则父级必须存在未删除，level=父+1；
     * 超过 MAX_LEVEL 返回 40001。
     */
    private int resolveLevel(Long parentId) {
        if (parentId == null || parentId == 0) {
            return 1;
        }
        ProductCategory parent = categoryMapper.selectById(parentId);
        if (parent == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "parentId 对应分类不存在");
        }
        int level = parent.getLevel() + 1;
        if (level > MAX_LEVEL) {
            throw new BizException(ResultCode.PARAM_INVALID,
                    "分类层级不能超过 " + MAX_LEVEL + " 级");
        }
        return level;
    }

    /** 判断 candidate 是否为 root 的子孙（沿 parentId 向上回溯，含任意深度）。 */
    private boolean isDescendant(Long candidate, Long root, Map<Long, ProductCategory> byId) {
        ProductCategory cur = byId.get(candidate);
        int guard = 0;
        while (cur != null && guard++ <= MAX_LEVEL + 10) {
            if (root.equals(cur.getId())) {
                return true;
            }
            cur = (cur.getParentId() == null || cur.getParentId() == 0) ? null : byId.get(cur.getParentId());
        }
        return false;
    }

    /** 级联重算 fromId 及其子孙的 level（fromLevel 为 fromId 的新层级）。 */
    private void cascadeLevel(Long fromId, int fromLevel, Map<Long, ProductCategory> byId) {
        List<ProductCategory> children = byId.values().stream()
                .filter(c -> fromId.equals(c.getParentId()))
                .toList();
        for (ProductCategory child : children) {
            if (child.getLevel() == fromLevel + 1) {
                continue; // 层级未变化，其子孙层级也必然一致，剪枝
            }
            child.setLevel(fromLevel + 1);
            categoryMapper.updateById(child);
            cascadeLevel(child.getId(), fromLevel + 1, byId);
        }
    }
}
