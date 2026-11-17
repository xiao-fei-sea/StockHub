package com.smartwms.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartwms.entity.SysDept;
import com.smartwms.common.enums.ResultCode;
import com.smartwms.common.BizException;
import com.smartwms.framework.security.LoginUser;
import com.smartwms.module.system.dto.DeptCreateDTO;
import com.smartwms.module.system.dto.DeptUpdateDTO;
import com.smartwms.module.system.mapper.SysDeptMapper;
import com.smartwms.module.system.mapper.SysUserMapper;
import com.smartwms.module.system.service.DeptService;
import com.smartwms.module.system.vo.DeptTreeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 部门管理服务实现（API-DEPT-001~004）。
 * 根节点 parentId=0、ancestors="0"（与 sys_menu/sys_dept 现有数据口径一致）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeptServiceImpl implements DeptService {

    /** 根父 id */
    private static final long ROOT_PARENT = 0L;

    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;

    // ==================== DEPT-001 部门树 ====================

    @Override
    public List<DeptTreeVO> tree(Integer status) {
        List<SysDept> depts = deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                .eq(status != null, SysDept::getStatus, status)
                .orderByAsc(SysDept::getSort)
                .orderByAsc(SysDept::getId));
        Map<Long, List<SysDept>> byParent = depts.stream()
                .collect(Collectors.groupingBy(SysDept::getParentId));
        // sort/id 已在 SQL 层排序，此处按插入顺序递归挂接
        return buildLevel(byParent, ROOT_PARENT);
    }

    // ==================== DEPT-002 新增 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(DeptCreateDTO dto) {
        String ancestors = resolveAncestors(dto.getParentId(), null); // 父不存在 → 404
        checkSiblingNameUnique(dto.getParentId(), dto.getDeptName(), null); // 同级重名 → 40904

        SysDept dept = new SysDept();
        dept.setParentId(dto.getParentId());
        dept.setDeptName(dto.getDeptName());
        dept.setSort(dto.getSort() == null ? 0 : dto.getSort());
        dept.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        dept.setAncestors(ancestors);
        dept.setCreateBy(currentUserId());
        deptMapper.insert(dept);
        log.info("新增部门成功 deptId={}, deptName={}, parentId={}, ancestors={}",
                dept.getId(), dept.getDeptName(), dto.getParentId(), ancestors);
        return dept.getId();
    }

    // ==================== DEPT-003 修改 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, DeptUpdateDTO dto) {
        SysDept dept = requireDept(id);
        if (dto.getParentId().equals(id)) {
            // 待确认：基线未定义「层级成环」错误码，文档指定按 40901 口径
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "不允许将部门挂到自身下");
        }
        String ancestors = resolveAncestors(dto.getParentId(), id);
        checkSiblingNameUnique(dto.getParentId(), dto.getDeptName(), id);

        boolean parentChanged = !dept.getParentId().equals(dto.getParentId());
        dept.setParentId(dto.getParentId());
        dept.setDeptName(dto.getDeptName());
        if (dto.getSort() != null) dept.setSort(dto.getSort());
        if (dto.getStatus() != null) dept.setStatus(dto.getStatus());
        dept.setAncestors(ancestors);
        dept.setUpdateBy(currentUserId());
        deptMapper.updateById(dept);

        if (parentChanged) {
            rebuildDescendants(id, ancestors); // 调父级后单事务递归重建整棵子树 ancestors
        }
        log.info("修改部门成功 deptId={}, parentId={}, parentChanged={}", id, dto.getParentId(), parentChanged);
    }

    // ==================== DEPT-004 删除 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysDept dept = requireDept(id);
        Long childCount = deptMapper.selectCount(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getParentId, id));
        if (childCount != null && childCount > 0) {
            // 待确认：基线未定义该错误码，文档建议复用 40901
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "存在下级部门，不允许删除");
        }
        Long userCount = userMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.smartwms.entity.SysUser>()
                .eq(com.smartwms.entity.SysUser::getDeptId, id));
        if (userCount != null && userCount > 0) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "存在用户引用该部门，不允许删除");
        }
        deptMapper.deleteById(id); // 逻辑删除 deleted=1
        log.info("删除部门成功 deptId={}, deptName={}", id, dept.getDeptName());
    }

    // ==================== 私有方法 ====================

    /** 部门必须存在且未删除，否则 404。 */
    private SysDept requireDept(Long id) {
        SysDept dept = deptMapper.selectById(id);
        if (dept == null) {
            throw new BizException(ResultCode.NOT_FOUND, "资源不存在");
        }
        return dept;
    }

    /**
     * 解析并校验父级：parentId=0 返回 "0"；父不存在返回 404；
     * parentId=deptId（update 时）或父位于待改节点后代中 → 40901 成环。
     */
    private String resolveAncestors(Long parentId, Long selfId) {
        if (parentId == ROOT_PARENT) {
            return "0";
        }
        SysDept parent = deptMapper.selectById(parentId);
        if (parent == null) {
            throw new BizException(ResultCode.NOT_FOUND, "父部门不存在");
        }
        if (selfId != null && isDescendant(parentId, selfId)) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "不允许将部门挂到其后代部门下");
        }
        return parent.getAncestors() + "," + parent.getId();
    }

    /** targetId 是否位于 ancestorId 的子树中（含自身）。 */
    private boolean isDescendant(Long targetId, Long ancestorId) {
        if (targetId.equals(ancestorId)) {
            return true;
        }
        SysDept target = deptMapper.selectById(targetId);
        return target != null && target.getAncestors() != null
                && java.util.Arrays.asList(target.getAncestors().split(","))
                .contains(String.valueOf(ancestorId));
    }

    /** 同级（同 parentId）部门名唯一校验，excludeId 为自身（修改场景排除）。 */
    private void checkSiblingNameUnique(Long parentId, String deptName, Long excludeId) {
        Long count = deptMapper.selectCount(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getParentId, parentId)
                .eq(SysDept::getDeptName, deptName)
                .ne(excludeId != null, SysDept::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException(ResultCode.CODE_EXISTS, "编码已存在");
        }
    }

    /**
     * 父级变化后递归重建全部后代 ancestors（base 为本节点新路径）。
     * 部门表规模小，按子表逐层查询（每层 IN 查询）在单事务内完成；
     * 上一层节点已先更新，父路径实时查库取最新值。
     */
    private void rebuildDescendants(Long id, String base) {
        List<Long> currentIds = new ArrayList<>(List.of(id));
        while (!currentIds.isEmpty()) {
            List<SysDept> children = deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                    .in(SysDept::getParentId, currentIds));
            if (children.isEmpty()) {
                break;
            }
            List<Long> nextIds = new ArrayList<>();
            for (SysDept child : children) {
                String parentAncestors = child.getParentId().equals(id)
                        ? base
                        : findAncestorsInLayer(children, child.getParentId(), base, id);
                child.setAncestors(parentAncestors + "," + child.getParentId());
                deptMapper.updateById(child);
                nextIds.add(child.getId());
            }
            currentIds = nextIds;
        }
    }

    /** 在本层缓存中取父节点新路径（父为上一层已更新节点，直接查库保证准确）。 */
    private String findAncestorsInLayer(List<SysDept> layer, Long parentId, String base, Long rootId) {
        if (parentId.equals(rootId)) {
            return base;
        }
        SysDept parent = deptMapper.selectById(parentId);
        return parent == null ? base : parent.getAncestors();
    }

    /** 构建指定父级下的树层。 */
    private List<DeptTreeVO> buildLevel(Map<Long, List<SysDept>> byParent, Long parentId) {
        List<SysDept> level = byParent.getOrDefault(parentId, List.of());
        List<DeptTreeVO> result = new ArrayList<>(level.size());
        for (SysDept d : level) {
            DeptTreeVO vo = new DeptTreeVO();
            vo.setId(d.getId());
            vo.setParentId(d.getParentId());
            vo.setDeptName(d.getDeptName());
            vo.setSort(d.getSort());
            vo.setAncestors(d.getAncestors());
            vo.setStatus(d.getStatus());
            vo.setChildren(buildLevel(byParent, d.getId()));
            result.add(vo);
        }
        result.sort(Comparator.comparing(DeptTreeVO::getSort, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(DeptTreeVO::getId));
        return result;
    }

    /** 当前登录用户 id。 */
    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser.getId();
        }
        return null;
    }
}
