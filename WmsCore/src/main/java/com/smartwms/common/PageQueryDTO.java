package com.smartwms.common;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 分页查询入参基类：统一 pageNum/pageSize 字段、校验与归一化。
 * <p>
 * 归一化规则（所有分页接口一致）：
 * <ul>
 *   <li>pageNum 缺省或 &lt; 1 时取 1；pageSize 缺省或 &lt; 1 时取 20；</li>
 *   <li>pageSize 超过上限 100 时统一截断为 100。</li>
 * </ul>
 * 各业务分页 DTO 继承本类即可，无需重复声明分页字段。
 */
@Data
public abstract class PageQueryDTO {

    @Min(value = 1, message = "pageNum 必须大于等于 1")
    private Integer pageNum = 1;

    @Min(value = 1, message = "pageSize 必须大于等于 1")
    private Integer pageSize = 20;

    /** 归一化后的页码（>= 1），非 JavaBean getter，避免 Jackson 重复序列化 */
    public int pageNum() {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    /** 归一化后的页大小（1~100，超出上限截断为 100） */
    public int pageSize() {
        int ps = pageSize == null || pageSize < 1 ? 20 : pageSize;
        return Math.min(ps, 100);
    }
}
