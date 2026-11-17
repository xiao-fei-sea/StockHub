package com.smartwms.module.product.service;

import com.smartwms.module.product.dto.CategoryCreateDTO;
import com.smartwms.module.product.dto.CategoryUpdateDTO;
import com.smartwms.module.product.vo.CategoryVO;

import java.util.List;

/** 商品分类服务（API-CAT-001~004）。 */
public interface CategoryService {

    /** CAT-001 分类树查询（sort/id 升序；keyword 保留命中节点及其祖先）。 */
    List<CategoryVO> tree(Integer status, String keyword);

    /** CAT-002 新增分类（code 全局唯一 40904，level 服务端维护，最大 5 级 40001），返回新分类 id。 */
    Long create(CategoryCreateDTO dto);

    /** CAT-003 修改分类（防环 40901，变更父级级联重算 level）。 */
    void update(Long id, CategoryUpdateDTO dto);

    /** CAT-004 删除分类（存在下级分类/商品引用时 40901，逻辑删除）。 */
    void delete(Long id);
}
