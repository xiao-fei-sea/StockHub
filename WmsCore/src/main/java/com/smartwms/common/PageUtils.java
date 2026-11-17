package com.smartwms.common;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 分页查询工具：统一「归一化分页参数 → selectPage → PageResult」流程，
 * 消除各 Service 中重复的分页样板代码。
 */
public final class PageUtils {

    private PageUtils() {
    }

    /**
     * 执行分页查询并转换为 PageResult。
     *
     * @param mapper    MyBatis-Plus BaseMapper
     * @param dto       分页入参（已归一化 pageNum/pageSize）
     * @param wrapper   查询条件
     * @param converter 记录列表转换函数（如 toVOList）
     */
    public static <T, V> PageResult<V> query(BaseMapper<T> mapper, PageQueryDTO dto,
                                             Wrapper<T> wrapper, Function<List<T>, List<V>> converter) {
        Page<T> page = mapper.selectPage(new Page<>(dto.pageNum(), dto.pageSize()), wrapper);
        return PageResult.of(page.getTotal(), dto.pageNum(), dto.pageSize(),
                converter.apply(page.getRecords()));
    }
}
