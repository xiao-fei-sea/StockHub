package com.smartwms.module.partner.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartwms.module.partner.mapper.CustomerMapper;
import com.smartwms.entity.Customer;
import com.smartwms.entity.SysUser;
import com.smartwms.common.enums.ResultCode;
import com.smartwms.common.BizException;
import com.smartwms.module.partner.dto.CustomerCreateDTO;
import com.smartwms.module.partner.dto.CustomerPageQueryDTO;
import com.smartwms.module.partner.dto.CustomerUpdateDTO;
import com.smartwms.module.partner.service.CustomerService;
import com.smartwms.module.partner.vo.CustomerOptionVO;
import com.smartwms.module.partner.vo.CustomerVO;
import com.smartwms.module.system.mapper.SysUserMapper;
import com.smartwms.common.PageResult;
import com.smartwms.common.PageUtils;
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
 * 客户服务实现（API-CUS-001~005）。
 * 客户为共享基础资料，不做数据仓库权限过滤。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    /** options 最多返回条数（CUS-005 规则3「待确认」采纳建议：限制 200 条）。 */
    private static final int OPTIONS_LIMIT = 200;

    private final CustomerMapper customerMapper;
    private final SysUserMapper userMapper;

    // ==================== CUS-001 分页查询 ====================

    @Override
    public PageResult<CustomerVO> page(CustomerPageQueryDTO dto) {

        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<Customer>()
                .eq(dto.getStatus() != null, Customer::getStatus, dto.getStatus())
                .orderByDesc(Customer::getId);
        if (StringUtils.hasText(dto.getKeyword())) {
            String like = "%" + dto.getKeyword().trim() + "%";
            wrapper.and(w -> w.like(Customer::getCustomerCode, like)
                    .or().like(Customer::getCustomerName, like)
                    .or().like(Customer::getContactName, like));
        }

        return PageUtils.query(customerMapper, dto, wrapper, this::toVOList);
    }

    // ==================== CUS-002 新增 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(CustomerCreateDTO dto) {
        // customerCode 全局唯一（uk_customer_code 并发兜底 → 40904）
        if (customerMapper.selectCount(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getCustomerCode, dto.getCustomerCode().trim())) > 0) {
            throw new BizException(ResultCode.CODE_EXISTS, "编码已存在");
        }

        Customer c = new Customer();
        c.setCustomerCode(dto.getCustomerCode().trim());
        c.setCustomerName(dto.getCustomerName().trim());
        c.setContactName(emptyToNull(dto.getContactName()));
        c.setContactPhone(emptyToNull(dto.getContactPhone()));
        c.setEmail(emptyToNull(dto.getEmail()));
        c.setAddress(emptyToNull(dto.getAddress()));
        c.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        c.setRemark(emptyToNull(dto.getRemark()));
        c.setCreateBy(currentUserId());
        customerMapper.insert(c);
        log.info("新增客户成功 customerId={}, customerCode={}", c.getId(), c.getCustomerCode());
        return c.getId();
    }

    // ==================== CUS-003 修改 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, CustomerUpdateDTO dto) {
        if (customerMapper.selectById(id) == null) {
            throw new BizException(ResultCode.NOT_FOUND, "资源不存在");
        }
        if (customerMapper.selectCount(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getCustomerCode, dto.getCustomerCode().trim())
                .ne(Customer::getId, id)) > 0) {
            throw new BizException(ResultCode.CODE_EXISTS, "编码已存在");
        }

        Customer c = new Customer();
        c.setId(id);
        c.setCustomerCode(dto.getCustomerCode().trim());
        c.setCustomerName(dto.getCustomerName().trim());
        c.setContactName(emptyToNull(dto.getContactName()));
        c.setContactPhone(emptyToNull(dto.getContactPhone()));
        c.setEmail(emptyToNull(dto.getEmail()));
        c.setAddress(emptyToNull(dto.getAddress()));
        if (dto.getStatus() != null) {
            c.setStatus(dto.getStatus());
        }
        c.setRemark(emptyToNull(dto.getRemark()));
        c.setUpdateBy(currentUserId());
        customerMapper.updateById(c);
        log.info("修改客户成功 customerId={}, customerCode={}", id, c.getCustomerCode());
    }

    // ==================== CUS-004 删除 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Customer c = customerMapper.selectById(id);
        if (c == null) {
            throw new BizException(ResultCode.NOT_FOUND, "资源不存在");
        }
        if (customerMapper.countRefs(id) > 0) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID,
                    "客户被出库单引用，不允许删除（请改用停用）");
        }
        customerMapper.deleteById(id); // 逻辑删除 deleted=1
        log.info("删除客户成功 customerId={}, customerCode={}", id, c.getCustomerCode());
    }

    // ==================== CUS-005 下拉选项 ====================

    @Override
    public List<CustomerOptionVO> options(String keyword, Integer status) {
        int st = status == null ? 1 : status;
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<Customer>()
                .eq(Customer::getStatus, st)
                .orderByAsc(Customer::getId)
                .last("LIMIT " + OPTIONS_LIMIT);
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim() + "%";
            wrapper.and(w -> w.like(Customer::getCustomerCode, like)
                    .or().like(Customer::getCustomerName, like));
        }
        return customerMapper.selectList(wrapper).stream()
                .map(c -> CustomerOptionVO.builder()
                        .id(c.getId())
                        .customerCode(c.getCustomerCode())
                        .customerName(c.getCustomerName())
                        .build())
                .toList();
    }

    // ==================== 私有方法 ====================

    /** 客户列表转 VO：批量反查操作人用户名（查不到置 null）。 */
    private List<CustomerVO> toVOList(List<Customer> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Set<Long> operatorIds = new HashSet<>();
        list.forEach(c -> {
            if (c.getCreateBy() != null) operatorIds.add(c.getCreateBy());
            if (c.getUpdateBy() != null) operatorIds.add(c.getUpdateBy());
        });
        Map<Long, String> operatorNames = operatorIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(operatorIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getUsername, (x, y) -> x));

        List<CustomerVO> result = new ArrayList<>(list.size());
        for (Customer c : list) {
            CustomerVO vo = new CustomerVO();
            vo.setId(c.getId());
            vo.setCustomerCode(c.getCustomerCode());
            vo.setCustomerName(c.getCustomerName());
            vo.setContactName(c.getContactName());
            vo.setContactPhone(c.getContactPhone());
            vo.setEmail(c.getEmail());
            vo.setAddress(c.getAddress());
            vo.setStatus(c.getStatus());
            vo.setRemark(c.getRemark());
            vo.setCreateTime(c.getCreateTime());
            vo.setUpdateTime(c.getUpdateTime());
            vo.setCreateBy(c.getCreateBy() == null ? null : operatorNames.get(c.getCreateBy()));
            vo.setUpdateBy(c.getUpdateBy() == null ? null : operatorNames.get(c.getUpdateBy()));
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
        if (auth != null && auth.getPrincipal() instanceof com.smartwms.framework.security.LoginUser loginUser) {
            return loginUser.getId();
        }
        return null;
    }
}
