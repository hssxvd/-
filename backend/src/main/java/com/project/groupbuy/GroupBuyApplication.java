package com.project.groupbuy;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 社区团购电商系统主启动类
 *
 * @author groupbuy
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
@MapperScan("com.project.groupbuy.modules.**.mapper")
public class GroupBuyApplication {

    public static void main(String[] args) {
        SpringApplication.run(GroupBuyApplication.class, args);
    }
}
