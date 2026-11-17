package com.smartwms.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwms.entity.SysMenu;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** sys_menu 数据访问。 */
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /** 按角色集合查询授权菜单 ID（去重）。 */
    @Select("<script>SELECT DISTINCT menu_id FROM sys_role_menu WHERE role_id IN "
            + "<foreach collection='roleIds' item='rid' open='(' separator=',' close=')'>#{rid}</foreach></script>")
    List<Long> selectMenuIdsByRoleIds(@Param("roleIds") List<Long> roleIds);
}
