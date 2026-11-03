package com.project.groupbuy.modules.supply.config;

import com.project.groupbuy.modules.supply.entity.Stock;
import com.project.groupbuy.modules.supply.mapper.StockMapper;
import com.project.groupbuy.modules.supply.service.StockCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时预热库存到 Redis
 *
 * Order(1) 保证比其他 CommandLineRunner 先跑
 * （避免其他预热依赖这个）
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class StockCacheWarmUp implements CommandLineRunner {

    private final StockMapper stockMapper;
    private final StockCacheService stockCacheService;

    @Override
    public void run(String... args) {
        try {
            List<Stock> all = stockMapper.selectList(null);
            stockCacheService.warmUp(all);
        } catch (Exception e) {
            log.error("[StockCache] 预热失败（Redis 不可用或 DB 异常），将使用 DB 降级：{}", e.getMessage());
        }
    }
}
