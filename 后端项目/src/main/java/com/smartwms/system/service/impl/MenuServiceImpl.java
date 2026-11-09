package com.smartwms.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartwms.entity.SysMenu;
import com.smartwms.entity.SysRoleMenu;
import com.smartwms.enums.ResultCode;
import com.smartwms.exception.BizException;
import com.smartwms.security.LoginUser;
import com.smartwms.system.dto.MenuCreateDTO;
import com.smartwms.system.dto.MenuUpdateDTO;
import com.smartwms.system.mapper.SysMenuMapper;
import com.smartwms.system.mapper.SysRoleMenuMapper;
import com.smartwms.system.service.MenuService;
import com.smartwms.system.vo.MenuTreeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜单权限管理服务实现（API-MENU-001~004）。
 * 层级不维护 ancestors 列，防环通过 parent 链回溯判断；
 * 类型层级匹配细则（如按钮不得作目录父级）文档标注待确认，暂不强制校验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MenuServiceImpl implements MenuService {

    /** 根父 id */
    private static final long ROOT_PARENT = 0L;
    /** 合法菜单类型 */
    private static final Set<String> MENU_TYPES = Set.of("C", "M", "B");
    private static final String TYPE_BUTTON = "B";
    private static final String TYPE_MENU = "M";

    private final SysMenuMapper menuMapper;
    private final SysRoleMenuMapper roleMenuMapper;

    // ==================== MENU-001 菜单树 ====================

    @Override
    public List<MenuTreeVO> tree(String menuType, Integer status) {
        List<SysMenu> menus = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .eq(menuType != null, SysMenu::getMenuType, menuType)
                .eq(status != null, SysMenu::getStatus, status)
                .orderByAsc(SysMenu::getSort)
                .orderByAsc(SysMenu::getId));
        Map<Long, List<SysMenu>> byParent = menus.stream()
                .collect(Collectors.groupingBy(SysMenu::getParentId));
        // 过滤后父节点可能不在结果集（如仅查 B 按钮），此类节点提升为根，避免整棵子树丢失
        Set<Long> ids = menus.stream().map(SysMenu::getId).collect(Collectors.toSet());
        List<MenuTreeVO> roots = new ArrayList<>();
        for (SysMenu m : menus) {
            if (m.getParentId() == ROOT_PARENT || !ids.contains(m.getParentId())) {
                roots.add(toNode(m, byParent));
            }
        }
        roots.sort(Comparator.comparing(MenuTreeVO::getSort, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(MenuTreeVO::getId));
        return roots;
    }

    // ==================== MENU-002 新增 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(MenuCreateDTO dto) {
        validateTypeFields(dto.getMenuType(), dto.getPerms(), dto.getPath(), dto.getComponent());
        resolveParent(dto.getParentId());
        checkSiblingNameUnique(dto.getParentId(), dto.getMenuName(), null);
        checkPermsUnique(dto.getMenuType(), dto.getPerms(), null);

        SysMenu menu = new SysMenu();
        menu.setParentId(dto.getParentId());
        menu.setMenuName(dto.getMenuName());
        menu.setMenuType(dto.getMenuType());
        menu.setPerms(emptyToNull(dto.getPerms()));
        menu.setPath(emptyToNull(dto.getPath()));
        menu.setComponent(emptyToNull(dto.getComponent()));
        menu.setIcon(emptyToNull(dto.getIcon()));
        menu.setSort(dto.getSort() == null ? 0 : dto.getSort());
        menu.setVisible(dto.getVisible() == null ? 1 : dto.getVisible());
        menu.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        menu.setCreateBy(currentUserId());
        menuMapper.insert(menu);
        log.info("新增菜单成功 menuId={}, menuName={}, menuType={}, parentId={}",
                menu.getId(), menu.getMenuName(), menu.getMenuType(), dto.getParentId());
        return menu.getId();
    }

    // ==================== MENU-003 修改 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, MenuUpdateDTO dto) {
        SysMenu menu = requireMenu(id);
        if (dto.getParentId().equals(id)) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "不允许将菜单挂到自身下");
        }
        if (dto.getParentId() != ROOT_PARENT && isAncestor(dto.getParentId(), id)) {
            // 待确认：基线未定义「层级成环」错误码，文档指定按 40901 口径
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "不允许将菜单挂到其子孙菜单下");
        }
        validateTypeFields(dto.getMenuType(), dto.getPerms(), dto.getPath(), dto.getComponent());
        resolveParent(dto.getParentId());
        checkSiblingNameUnique(dto.getParentId(), dto.getMenuName(), id);
        checkPermsUnique(dto.getMenuType(), dto.getPerms(), id);

        menu.setParentId(dto.getParentId());
        menu.setMenuName(dto.getMenuName());
        menu.setMenuType(dto.getMenuType());
        menu.setPerms(emptyToNull(dto.getPerms()));
        menu.setPath(emptyToNull(dto.getPath()));
        menu.setComponent(emptyToNull(dto.getComponent()));
        menu.setIcon(emptyToNull(dto.getIcon()));
        if (dto.getSort() != null) menu.setSort(dto.getSort());
        if (dto.getVisible() != null) menu.setVisible(dto.getVisible());
        if (dto.getStatus() != null) menu.setStatus(dto.getStatus());
        menu.setUpdateBy(currentUserId());
        menuMapper.updateById(menu);
        log.info("修改菜单成功 menuId={}, parentId={}, menuType={}", id, dto.getParentId(), dto.getMenuType());
    }

    // ==================== MENU-004 删除 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysMenu menu = requireMenu(id);
        Long childCount = menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getParentId, id));
        if (childCount != null && childCount > 0) {
            // 待确认：首期不提供级联删除，自底向上删除
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "存在下级菜单，不允许删除");
        }
        menuMapper.deleteById(id); // 逻辑删除 deleted=1
        // 物理清理 sys_role_menu，避免已删菜单的权限残留
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getMenuId, id));
        log.info("删除菜单成功 menuId={}, menuName={}", id, menu.getMenuName());
    }

    // ==================== 私有方法 ====================

    /** 菜单必须存在且未逻辑删除，否则 404。 */
    private SysMenu requireMenu(Long id) {
        SysMenu menu = menuMapper.selectById(id);
        if (menu == null) {
            throw new BizException(ResultCode.NOT_FOUND, "资源不存在");
        }
        return menu;
    }

    /** 按 menuType 校验条件必填字段：B 必填 perms，M 必填 path/component；menuType 非法返回 40001。 */
    private void validateTypeFields(String menuType, String perms, String path, String component) {
        if (!MENU_TYPES.contains(menuType)) {
            throw new BizException(ResultCode.PARAM_INVALID, "menuType 仅允许 C/M/B");
        }
        if (TYPE_BUTTON.equals(menuType) && !org.springframework.util.StringUtils.hasText(perms)) {
            throw new BizException(ResultCode.PARAM_INVALID, "按钮菜单必须填写 perms");
        }
        if (TYPE_MENU.equals(menuType)
                && (!org.springframework.util.StringUtils.hasText(path)
                || !org.springframework.util.StringUtils.hasText(component))) {
            throw new BizException(ResultCode.PARAM_INVALID, "菜单必须填写 path 与 component");
        }
    }

    /** parentId 须为 0 或存在的菜单 id（文档 MENU-002 错误码仅列 40001，父缺失按 40001 处理）。 */
    private void resolveParent(Long parentId) {
        if (parentId == ROOT_PARENT) {
            return;
        }
        if (menuMapper.selectById(parentId) == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "parentId 对应菜单不存在");
        }
    }

    /** targetId 是否位于 ancestorId 的父链上（含自身），用于移动防环。 */
    private boolean isAncestor(Long targetId, Long ancestorId) {
        Long cursor = targetId;
        Set<Long> visited = new HashSet<>();
        while (cursor != null && cursor != ROOT_PARENT && visited.add(cursor)) {
            if (cursor.equals(ancestorId)) {
                return true;
            }
            SysMenu node = menuMapper.selectById(cursor);
            if (node == null) {
                return false;
            }
            cursor = node.getParentId();
        }
        return false;
    }

    /** 同级（同 parentId）菜单名唯一，excludeId 为自身（修改场景排除）。 */
    private void checkSiblingNameUnique(Long parentId, String menuName, Long excludeId) {
        Long count = menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getParentId, parentId)
                .eq(SysMenu::getMenuName, menuName)
                .ne(excludeId != null, SysMenu::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException(ResultCode.CODE_EXISTS, "编码已存在");
        }
    }

    /** perms 全局唯一（仅 B 类型必填时校验，排除自身），重复返回 40904。 */
    private void checkPermsUnique(String menuType, String perms, Long excludeId) {
        if (!TYPE_BUTTON.equals(menuType) || !org.springframework.util.StringUtils.hasText(perms)) {
            return;
        }
        Long count = menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getPerms, perms)
                .ne(excludeId != null, SysMenu::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException(ResultCode.CODE_EXISTS, "编码已存在");
        }
    }

    /** 构建节点并递归挂接子级（byParent 键为父 id，SQL 已按 sort、id 升序，此处兜底排序）。 */
    private MenuTreeVO toNode(SysMenu m, Map<Long, List<SysMenu>> byParent) {
        MenuTreeVO vo = new MenuTreeVO();
        vo.setId(m.getId());
        vo.setParentId(m.getParentId());
        vo.setMenuName(m.getMenuName());
        vo.setMenuType(m.getMenuType());
        vo.setPerms(m.getPerms());
        vo.setPath(m.getPath());
        vo.setComponent(m.getComponent());
        vo.setIcon(m.getIcon());
        vo.setSort(m.getSort());
        vo.setVisible(m.getVisible());
        vo.setStatus(m.getStatus());
        List<MenuTreeVO> children = new ArrayList<>();
        for (SysMenu child : byParent.getOrDefault(m.getId(), List.of())) {
            children.add(toNode(child, byParent));
        }
        children.sort(Comparator.comparing(MenuTreeVO::getSort, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(MenuTreeVO::getId));
        vo.setChildren(children);
        return vo;
    }

    /** 当前登录用户 id。 */
    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser.getId();
        }
        return null;
    }

    /** 空串归一为 null（可选字段清空）。 */
    private String emptyToNull(String s) {
        return org.springframework.util.StringUtils.hasText(s) ? s.trim() : null;
    }
}
