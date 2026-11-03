package com.project.groupbuy.modules.order.controller;

import com.project.groupbuy.common.context.SecurityContext;
import com.project.groupbuy.common.exception.BusinessException;
import com.project.groupbuy.common.result.ErrorCode;
import com.project.groupbuy.common.result.R;
import com.project.groupbuy.modules.order.entity.Orders;
import com.project.groupbuy.modules.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 订单控制器（约束文档 4.3 特色属性快照 + 5.2 订单状态机）
 *
 * v1.18 下单幂等：create 入口加 Redis SETNX 锁
 *   key = "order_submit:{userId}"，TTL = 30s
 *   用户狂点"提交订单"时，第二次进来 SETNX 返回 null → 直接拒绝
 */
@Slf4j
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final StringRedisTemplate redis;

    /**
     * 创建订单（业务闭环起点）
     * 【约束 §8 拆单】返回 List<Orders>：一个购物车里有多个商家的商品时，会拆成多笔独立订单
     */
    @PostMapping
    public R<List<Orders>> create(@RequestBody Map<String, Object> body) {
        Long userId = SecurityContext.getUserId();

        // ✅ Redis 幂等锁：SETNX 30s，防狂点生成多笔订单
        // Redis 挂了 → Boolean.TRUE 降级放行（Redis 异常不挡主流程）
        boolean locked;
        try {
            Boolean ok = redis.opsForValue().setIfAbsent(
                    "order_submit:" + userId, "1", 30, TimeUnit.SECONDS);
            locked = Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            log.warn("[幂等] Redis SETNX 异常，降级放行 userId={}: {}", userId, e.getMessage());
            locked = true;
        }
        if (!locked) {
            log.warn("[幂等] 用户 {} 狂点下单被拦截", userId);
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请勿重复提交，30 秒后再试");
        }

        Long communityId = Long.valueOf(body.get("communityId").toString());
        Long pickupPointId = body.get("pickupPointId") != null ?
                Long.valueOf(body.get("pickupPointId").toString()) : null;
        String remark = (String) body.get("remark");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemMaps = (List<Map<String, Object>>) body.get("items");
        List<OrderService.OrderItemCreateItem> items;
        if (itemMaps != null && !itemMaps.isEmpty()) {
            items = itemMaps.stream().map(m -> {
                OrderService.OrderItemCreateItem item = new OrderService.OrderItemCreateItem();
                item.setSkuId(Long.valueOf(m.get("skuId").toString()));
                item.setQuantity(Integer.valueOf(m.get("quantity").toString()));
                return item;
            }).toList();
        } else {
            items = List.of();
        }

        List<Orders> orders = orderService.create(communityId, pickupPointId, items, remark);

        // 下单成功 → 清掉锁，用户可以再次下单（不用等 30s）
        try { redis.delete("order_submit:" + userId); } catch (Exception ignored) {}

        return R.ok(orders);
    }

    /**
     * 取消订单（消费者，待支付状态）
     */
    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id, @RequestParam(defaultValue = "用户主动取消") String reason) {
        orderService.cancel(id, reason);
        return R.ok();
    }

    /**
     * 订单详情（按主键 id）
     */
    @GetMapping("/{id:\\d+}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(orderService.detail(id));
    }

    /**
     * 订单详情（按业务编号 orderNo，约束 7.2.3：前端用 orderNo 查单）
     */
    @GetMapping("/no/{orderNo}")
    public R<Map<String, Object>> detailByOrderNo(@PathVariable String orderNo) {
        return R.ok(orderService.detailByOrderNo(orderNo));
    }

    /**
     * 我的订单（消费者），每条聚合 OrderItem 商品快照
     */
    @GetMapping("/mine")
    public R<List<Map<String, Object>>> mine() {
        Long userId = SecurityContext.getUserId();
        return R.ok(orderService.listMine(userId));
    }

    /**
     * 商家订单列表（数据隔离 + 聚合商品快照）
     */
    @GetMapping("/merchant")
    public R<List<Map<String, Object>>> merchant(
            @RequestParam(required = false) String status) {
        return R.ok(orderService.listByMerchant(status));
    }
}
