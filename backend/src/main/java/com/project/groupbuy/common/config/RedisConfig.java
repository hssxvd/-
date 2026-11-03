package com.project.groupbuy.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 基础配置（约束文档 28.18：库存预检必须走 Redis，不能全打 MySQL）
 *
 * 只用 StringRedisTemplate：
 *   - key:   "stock:a:{stockId}"        → availableQty（Integer 字符串化）
 *   - value: 纯数字字符串，如 "30"
 * 好处：无序列化开销，数字直接 INCRBY/DECRBY，1ms 响应。
 */
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
