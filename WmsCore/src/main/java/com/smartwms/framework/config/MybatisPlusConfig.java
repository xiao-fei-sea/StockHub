package com.smartwms.framework.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置：分页插件 + 乐观锁插件（单据 version 防并发，基线第 7 节三重防护之一）。
 * Mapper 按业务模块分包（com.smartwms.module.{模块}.mapper），新增模块在此追加包名。
 */
@Configuration
@MapperScan({
        "com.smartwms.module.system.mapper",
        "com.smartwms.module.warehouse.mapper",
        "com.smartwms.module.storage.mapper",
        "com.smartwms.module.product.mapper",
        "com.smartwms.module.partner.mapper",
        "com.smartwms.module.inbound.mapper",
        "com.smartwms.module.outbound.mapper",
        "com.smartwms.module.inventory.mapper",
        "com.smartwms.module.stocktake.mapper",
        "com.smartwms.module.transfer.mapper",
        "com.smartwms.module.alert.mapper",
        "com.smartwms.module.stats.mapper",
        "com.smartwms.module.log.mapper"
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
