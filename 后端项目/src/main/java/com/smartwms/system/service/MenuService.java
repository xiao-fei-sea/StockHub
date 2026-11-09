package com.smartwms.system.service;

import com.smartwms.system.dto.MenuCreateDTO;
import com.smartwms.system.dto.MenuUpdateDTO;
import com.smartwms.system.vo.MenuTreeVO;

import java.util.List;

/**
 * 菜单权限管理服务（API-MENU-001~004，详见《角色权限接口文档》第 2 节）。
 */
public interface MenuService {

    /** MENU-001 菜单树查询（menuType/status 可选过滤，按 sort、id 升序）。 */
    List<MenuTreeVO> tree(String menuType, Integer status);

    /** MENU-002 新增目录/菜单/按钮节点。 */
    Long create(MenuCreateDTO dto);

    /** MENU-003 修改菜单节点（含防环校验）。 */
    void update(Long id, MenuUpdateDTO dto);

    /** MENU-004 删除菜单节点（有下级拒绝；清理 sys_role_menu 关联）。 */
    void delete(Long id);
}
