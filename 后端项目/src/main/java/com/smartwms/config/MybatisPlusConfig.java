package com.smartwms.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置：分页插件 + 乐观锁插件（单据 version 防并发，基线第 7 节三重防护之一）。
 * Mapper 扫描按业务域分包（《后端开发规范》第 2.1 节），新增模块在此追加包名。
 */
@Configuration
@MapperScan({
        "com.smartwms.system.mapper",
        "com.smartwms.base.mapper",
        "com.smartwms.inbound.mapper",
        "com.smartwms.outbound.mapper",
        "com.smartwms.inventory.mapper",
        "com.smartwms.stocktake.mapper",
        "com.smartwms.transfer.mapper",
        "com.smartwms.stats.mapper",
        "com.smartwms.log.mapper"
})
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
