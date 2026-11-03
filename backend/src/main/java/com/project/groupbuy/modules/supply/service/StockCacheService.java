package com.project.groupbuy.modules.supply.service;

import com.project.groupbuy.modules.supply.entity.Stock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;

/**
 * 库存 Redis 预检层（高并发下单第一道门）
 *
 * 设计原则：
 *   1. Redis 只做「预检」不做「真实扣减」—— 真实扣减仍然走 MySQL 条件更新
 *      （约束文档 §6：数据库是最终真实数据源，Redis 只挡流量）
 *   2. DECRBY 结果 >= 0 → 放行；< 0 → INCRBY 回滚 + 直接返回超卖（不再查 MySQL）
 *   3. Redis 与 MySQL 不一致时，以 MySQL 为准（DB 失败后 Redis 回滚）
 *
 * Key 规范：
 *   stock:a:{stockId}  →  available_qty（纯数字字符串，如 "30"）
 *   示例：stock:a:123    →  30
 *
 * 为什么用 String 不用 Hash？
 *   - DECRBY/INCRBY 对 String 是 O(1) 原子操作
 *   - Hash 要 HMSET 才能原子多字段，但我们只需要 available_qty
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockCacheService {

    private final StringRedisTemplate redis;

    private static final String KEY_PREFIX = "stock:a:";

    private static String key(Long stockId) {
        return KEY_PREFIX + stockId;
    }

    /**
     * 启动预热：全量 stock 表写入 Redis
     */
    public void warmUp(Collection<Stock> stocks) {
        if (stocks == null || stocks.isEmpty()) return;
        int ok = 0;
        for (Stock s : stocks) {
            if (s.getId() == null) continue;
            int avail = s.getAvailableQty() == null ? 0 : s.getAvailableQty();
            redis.opsForValue().set(key(s.getId()), String.valueOf(avail));
            ok++;
        }
        log.info("[StockCache] 预热完成：{} 条 stock 同步到 Redis", ok);
    }

    /**
     * 查 Redis 可用库存（返回 null = 缓存 miss，调用方应 fallback MySQL）
     */
    public Integer getAvailable(Long stockId) {
        String v = redis.opsForValue().get(key(stockId));
        if (v == null) return null;
        try { return Integer.parseInt(v); } catch (NumberFormatException e) { return null; }
    }

    /**
     * 高并发预检：原子 DECRBY
     *
     * @return true 扣减成功（Redis 库存 >= qty） → 可以继续走 MySQL 真实锁库
     *         false 库存不足 → 直接返回超卖，不再查 DB
     *
     * 注意：DECRBY 可能把库存扣成负数（原子性代价），但我们**不让它负**——
     * 先 INCRBY 回滚，再返回 false。
     * 这保证 Redis 库存永远 >= 0，下次下单不会看到负库存。
     */
    public boolean tryDecr(Long stockId, int qty) {
        if (stockId == null || qty <= 0) return false;
        String k = key(stockId);
        try {
            Long remaining = redis.opsForValue().decrement(k, qty);
            if (remaining == null) {
                // key 不存在（缓存 miss），递增回去让 Redis 状态不变，返回 false 让调用方 fallback MySQL
                redis.opsForValue().increment(k, qty);
                return false;
            }
            if (remaining < 0) {
                // 库存不足 → INCRBY 回滚，保证 Redis 不出现负数
                redis.opsForValue().increment(k, qty);
                log.debug("[StockCache] 预检拒绝：stockId={} qty={} Redis不足", stockId, qty);
                return false;
            }
            return true;
        } catch (Exception e) {
            // Redis 挂了 → 返回 true 让请求继续走 MySQL（降级，Redis 失败不影响主流程）
            log.warn("[StockCache] Redis DECR 异常，降级放行 stockId={}: {}", stockId, e.getMessage());
            return true;
        }
    }

    /**
     * MySQL 锁库成功后，将 Redis 库存同步为 MySQL 真实值（可选，当前仅用于观测）
     * 默认不主动写，让 DECRBY 自己维护。只有 invalidate 时才 SET。
     */
    public void syncAvailable(Long stockId, int available) {
        redis.opsForValue().set(key(stockId), String.valueOf(available));
    }

    /**
     * MySQL 事务失败 → 把刚 DECR 下去的 Redis 库存 INCR 回来
     */
    public void rollbackDecr(Long stockId, int qty) {
        if (stockId == null || qty <= 0) return;
        try {
            redis.opsForValue().increment(key(stockId), qty);
        } catch (Exception e) {
            log.warn("[StockCache] Redis 回滚异常 stockId={}: {}", stockId, e.getMessage());
        }
    }

    /**
     * 全清（紧急兜底，比如 Redis 和 MySQL 偏差太大）
     */
    public void clear() {
        var keys = redis.keys(KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
            log.warn("[StockCache] 清空 Redis 库存缓存：{} 条", keys.size());
        }
    }
}
