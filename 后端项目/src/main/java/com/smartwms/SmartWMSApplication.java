package com.smartwms;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 智仓云 SmartWMS 后端启动类。
 *
 * <p>启动前请确认：
 * <ul>
 *   <li>MySQL 8.0 已按《数据库脚本》完成 smartwms 库搭建；</li>
 *   <li>环境变量 DB_URL / DB_USER / DB_PASSWORD、JWT_SECRET 已配置（见 application.yml）。</li>
 * </ul>
 */
@SpringBootApplication
@MapperScan("com.smartwms.mapper")
@EnableScheduling
public class SmartWMSApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartWMSApplication.class, args);
    }
}
