package com.smartwms.partner.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.entity.Supplier;
import com.smartwms.entity.SysUser;
import com.smartwms.enums.ResultCode;
import com.smartwms.exception.BizException;
import com.smartwms.base.mapper.SupplierMapper;
import com.smartwms.partner.dto.SupplierCreateDTO;
import com.smartwms.partner.dto.SupplierPageQueryDTO;
import com.smartwms.partner.dto.SupplierUpdateDTO;
import com.smartwms.partner.service.SupplierService;
import com.smartwms.partner.vo.SupplierOptionVO;
import com.smartwms.partner.vo.SupplierVO;
import com.smartwms.system.mapper.SysUserMapper;
import com.smartwms.vo.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 供应商服务实现（API-SUP-001~005）。
 * 供应商为共享基础资料，不做数据仓库权限过滤。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierServiceImpl implements SupplierService {

    /** options 最多返回条数（SUP-005 规则3「待确认」采纳建议：限制 200 条）。 */
    private static final int OPTIONS_LIMIT = 200;

    private final SupplierMapper supplierMapper;
    private final SysUserMapper userMapper;

    // ==================== SUP-001 分页查询 ====================

    @Override
    public PageResult<SupplierVO> page(SupplierPageQueryDTO dto) {
        int pageNum = dto.getPageNum() == null ? 1 : dto.getPageNum();
        int pageSize = dto.getPageSize() == null ? 20 : Math.min(dto.getPageSize(), 100);

        LambdaQueryWrapper<Supplier> wrapper = new LambdaQueryWrapper<Supplier>()
                .eq(dto.getStatus() != null, Supplier::getStatus, dto.getStatus())
                .orderByDesc(Supplier::getId);
        if (StringUtils.hasText(dto.getKeyword())) {
            String like = "%" + dto.getKeyword().trim() + "%";
            wrapper.and(w -> w.like(Supplier::getSupplierCode, like)
                    .or().like(Supplier::getSupplierName, like)
                    .or().like(Supplier::getContactName, like));
        }

        Page<Supplier> page = supplierMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page.getTotal(), pageNum, pageSize, toVOList(page.getRecords()));
    }

    // ==================== SUP-002 新增 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SupplierCreateDTO dto) {
        // supplierCode 全局唯一（uk_supplier_code 并发兜底 → 40904）
        if (supplierMapper.selectCount(new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getSupplierCode, dto.getSupplierCode().trim())) > 0) {
            throw new BizException(ResultCode.CODE_EXISTS, "编码已存在");
        }

        Supplier s = new Supplier();
        s.setSupplierCode(dto.getSupplierCode().trim());
        s.setSupplierName(dto.getSupplierName().trim());
        s.setContactName(emptyToNull(dto.getContactName()));
        s.setContactPhone(emptyToNull(dto.getContactPhone()));
        s.setEmail(emptyToNull(dto.getEmail()));
        s.setAddress(emptyToNull(dto.getAddress()));
        s.setBankInfo(emptyToNull(dto.getBankInfo()));
        s.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        s.setRemark(emptyToNull(dto.getRemark()));
        s.setCreateBy(currentUserId());
        supplierMapper.insert(s);
        log.info("新增供应商成功 supplierId={}, supplierCode={}", s.getId(), s.getSupplierCode());
        return s.getId();
    }

    // ==================== SUP-003 修改 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SupplierUpdateDTO dto) {
        if (supplierMapper.selectById(id) == null) {
            throw new BizException(ResultCode.NOT_FOUND, "资源不存在");
        }
        // 电话/邮箱格式复用 DTO @Pattern/@Email（40001）
        if (supplierMapper.selectCount(new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getSupplierCode, dto.getSupplierCode().trim())
                .ne(Supplier::getId, id)) > 0) {
            throw new BizException(ResultCode.CODE_EXISTS, "编码已存在");
        }

        Supplier s = new Supplier();
        s.setId(id);
        s.setSupplierCode(dto.getSupplierCode().trim());
        s.setSupplierName(dto.getSupplierName().trim());
        s.setContactName(emptyToNull(dto.getContactName()));
        s.setContactPhone(emptyToNull(dto.getContactPhone()));
        s.setEmail(emptyToNull(dto.getEmail()));
        s.setAddress(emptyToNull(dto.getAddress()));
        s.setBankInfo(emptyToNull(dto.getBankInfo()));
        if (dto.getStatus() != null) {
            s.setStatus(dto.getStatus());
        }
        s.setRemark(emptyToNull(dto.getRemark()));
        s.setUpdateBy(currentUserId());
        supplierMapper.updateById(s);
        log.info("修改供应商成功 supplierId={}, supplierCode={}", id, s.getSupplierCode());
    }

    // ==================== SUP-004 删除 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Supplier s = supplierMapper.selectById(id);
        if (s == null) {
            throw new BizException(ResultCode.NOT_FOUND, "资源不存在");
        }
        if (supplierMapper.countRefs(id) > 0) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID,
                    "供应商被入库单或批次库存引用，不允许删除（请改用停用）");
        }
        supplierMapper.deleteById(id); // 逻辑删除 deleted=1
        log.info("删除供应商成功 supplierId={}, supplierCode={}", id, s.getSupplierCode());
    }

    // ==================== SUP-005 下拉选项 ====================

    @Override
    public List<SupplierOptionVO> options(String keyword, Integer status) {
        int st = status == null ? 1 : status;
        LambdaQueryWrapper<Supplier> wrapper = new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getStatus, st)
                .orderByAsc(Supplier::getId)
                .last("LIMIT " + OPTIONS_LIMIT);
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim() + "%";
            wrapper.and(w -> w.like(Supplier::getSupplierCode, like)
                    .or().like(Supplier::getSupplierName, like));
        }
        return supplierMapper.selectList(wrapper).stream()
                .map(s -> SupplierOptionVO.builder()
                        .id(s.getId())
                        .supplierCode(s.getSupplierCode())
                        .supplierName(s.getSupplierName())
                        .build())
                .toList();
    }

    // ==================== 私有方法 ====================

    /** 供应商列表转 VO：批量反查操作人用户名（查不到置 null）。 */
    private List<SupplierVO> toVOList(List<Supplier> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Set<Long> operatorIds = new HashSet<>();
        list.forEach(s -> {
            if (s.getCreateBy() != null) operatorIds.add(s.getCreateBy());
            if (s.getUpdateBy() != null) operatorIds.add(s.getUpdateBy());
        });
        Map<Long, String> operatorNames = operatorIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(operatorIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getUsername, (x, y) -> x));

        List<SupplierVO> result = new ArrayList<>(list.size());
        for (Supplier s : list) {
            SupplierVO vo = new SupplierVO();
            vo.setId(s.getId());
            vo.setSupplierCode(s.getSupplierCode());
            vo.setSupplierName(s.getSupplierName());
            vo.setContactName(s.getContactName());
            vo.setContactPhone(s.getContactPhone());
            vo.setEmail(s.getEmail());
            vo.setAddress(s.getAddress());
            vo.setBankInfo(s.getBankInfo());
            vo.setStatus(s.getStatus());
            vo.setRemark(s.getRemark());
            vo.setCreateTime(s.getCreateTime());
            vo.setUpdateTime(s.getUpdateTime());
            vo.setCreateBy(s.getCreateBy() == null ? null : operatorNames.get(s.getCreateBy()));
            vo.setUpdateBy(s.getUpdateBy() == null ? null : operatorNames.get(s.getUpdateBy()));
            result.add(vo);
        }
        return result;
    }

    /** 空白字符串转 null。 */
    private String emptyToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    /** 当前登录用户 id（操作人）。 */
    private Long currentUserId() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.smartwms.security.LoginUser loginUser) {
            return loginUser.getId();
        }
        return null;
    }
}
