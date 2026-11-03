package com.project.groupbuy.modules.payment.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.project.groupbuy.common.exception.BusinessException;
import com.project.groupbuy.common.result.ErrorCode;
import com.project.groupbuy.modules.order.entity.OrderItem;
import com.project.groupbuy.modules.order.entity.Orders;
import com.project.groupbuy.modules.order.mapper.OrderItemMapper;
import com.project.groupbuy.modules.order.mapper.OrderStatusLogMapper;
import com.project.groupbuy.modules.order.mapper.OrdersMapper;
import com.project.groupbuy.modules.fulfillment.service.FulfillmentService;
import com.project.groupbuy.modules.payment.entity.Payment;
import com.project.groupbuy.modules.payment.entity.PaymentCallbackLog;
import com.project.groupbuy.modules.payment.mapper.PaymentCallbackLogMapper;
import com.project.groupbuy.modules.payment.mapper.PaymentMapper;
import com.project.groupbuy.modules.supply.entity.Stock;
import com.project.groupbuy.modules.supply.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import com.project.groupbuy.common.constant.OrderStatusConstant;

/**
 * 模拟支付服务（约束文档 5.3 幂等回调）
 *
 * 支付状态机：PENDING → SUCCESS / FAILED / TIMEOUT
 * 退款状态机：SUCCESS → REFUNDING → REFUNDED
 *
 * 支付成功驱动：orders.PENDING_PAY → PAID + 库存预占转实扣
 * 超时释放驱动：定时扫描 → 取消订单 + 释放库存
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentMapper paymentMapper;
    private final PaymentCallbackLogMapper callbackLogMapper;
    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStatusLogMapper orderStatusLogMapper;
    private final StockService stockService;
    private final FulfillmentService fulfillmentService;

    /** 自引用代理（用于 REQUIRES_NEW 等事务传播，Spring AOP 要求通过代理调用） */
    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private PaymentService self;

    /**
     * 创建支付单
     */
    @Transactional(rollbackFor = Exception.class)
    public Payment create(Long orderId) {
        Orders order = ordersMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "订单不存在");
        }
        if (!OrderStatusConstant.PENDING_PAY.equals(order.getOrderStatus())) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS, "订单状态不允许支付");
        }
        if (OrderStatusConstant.PAID.equals(order.getPayStatus())) {
            throw new BusinessException(ErrorCode.PAY_ALREADY_PAID);
        }

        Payment payment = new Payment();
        payment.setPaymentNo("PAY" + IdUtil.getSnowflakeNextIdStr());
        payment.setOrderId(orderId);
        payment.setOrderNo(order.getOrderNo());
        payment.setUserId(order.getUserId());
        payment.setAmount(order.getPayAmount());
        payment.setPayType("MOCK");
        payment.setPayStatus("PENDING");
        paymentMapper.insert(payment);
        return payment;
    }

    /**
     * 模拟支付回调（约束文档 5.3 幂等）
     * 驱动：① 支付状态 → SUCCESS  ② 订单 PENDING_PAY → PAID  ③ 库存预占转实扣
     */
    @Transactional(rollbackFor = Exception.class)
    public void mockCallback(String paymentNo) {
        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>().eq(Payment::getPaymentNo, paymentNo));
        if (payment == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "支付单不存在");
        }
        // 幂等：已是终态直接返回
        if ("SUCCESS".equals(payment.getPayStatus()) || "REFUNDED".equals(payment.getPayStatus())) {
            log.info("[支付回调] 已处理，跳过: {}", paymentNo);
            writeCallbackLog(paymentNo, "PAY", null, "DUPLICATE", null);
            return;
        }

        // ① 更新支付状态
        payment.setPayStatus("SUCCESS");
        payment.setCallbackTime(LocalDateTime.now());
        paymentMapper.updateById(payment);

        // ② 更新订单状态
        Orders order = ordersMapper.selectById(payment.getOrderId());
        if (OrderStatusConstant.PENDING_PAY.equals(order.getOrderStatus())) {
            String oldStatus = order.getOrderStatus();
            order.setOrderStatus(OrderStatusConstant.PAID);
            order.setPayStatus("SUCCESS");
            order.setPayTime(LocalDateTime.now());
            // 约束 5.2 PENDING_PICKUP 配套：自提订单支付成功后立即生成自提核销码
            // 约束文档 8 自提码规范：8 位纯数字（原 ZT 前缀已在 v1.17 移除）
            if (order.getPickupPointId() != null && order.getPickupCode() == null) {
                String code = order.getOrderNo().substring(order.getOrderNo().length() - 6)
                        + String.format("%02d", (int)(Math.random() * 100));
                order.setPickupCode(code);
                log.info("[自提码] 订单 {} 生成 pickupCode={}", order.getOrderNo(), code);
            }
            ordersMapper.updateById(order);
            writeOrderLog(order.getId(), order.getOrderNo(), oldStatus, OrderStatusConstant.PAID, "ORDER", null, "支付成功");
        }

        // ✅ Bug1 修复（v1.19）：删除这里的 deductStockLocked 调用
        //
        // 【根因】PaymentService.mockCallback() + FulfillmentService.transition(COMPLETED)
        // 两者都调 stockService.deduct() → total_qty 被双倍扣减。
        // 之前核销时报"预占库存不足"就是因为：支付已扣一次，核销时 locked 已为 0，
        // 只能走"降级只扣 total"路径 → total 再次减少 → 账实不符。
        //
        // 【正确生命周期】：
        //   下单 → lock（locked += qty, available -= qty）
        //   支付 → 仅状态变 PAID，库存不动
        //   核销 COMPLETED → 唯一一次 deduct（locked -= qty, total -= qty）
        //
        // StockService.deduct() 的"预占已消耗→降级只扣 total"逻辑继续保留，
        // 作为对历史脏数据（曾被双扣过）的幂等兜底。

        // ④ v1.13 修复：不再自动驱动履约
        //   支付成功后订单停在 PAID（label="待处理"），等待商家主动调用 FulfillmentService.assign()
        //   自提码已在上方提前生成（PENDING_PICKUP 状态时消费者端才展示）
        //
        //   状态链（自提）：PENDING_PAY → PAID → PROCESSING → PENDING_PICKUP → COMPLETED
        //                                    ↑支付           ↑商家分配       ↑商家送到自提点  ↑核销员扫码

        writeCallbackLog(paymentNo, "PAY", null, "SUCCESS", null);
        log.info("[支付回调] 成功: {}", paymentNo);
    }

    // 【v1.19 已删除】deductStockLocked() 方法
    // 根因：和 FulfillmentService.transition(COMPLETED) 重复调用 stockService.deduct()
    // → total_qty 被双倍扣减。库存预占转实扣只在 Fulfillment COMPLETED 时发生一次。

    /**
     * 退款（无幂等键版本，兼容历史调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public String refund(Long orderId, BigDecimal refundAmount, String reason) {
        return refund(orderId, refundAmount, reason, null);
    }

    /**
     * 退款（约束文档 5.5 退款联动 + 5.3 幂等 + 5.2 订单状态机）
     *
     * 驱动：
     *   ① 支付单 SUCCESS → REFUNDED + refund_amount 累加
     *   ② orders.refunded_amount 累加
     *   ③ 订单主状态：全额退款且原状态非 COMPLETED（PAID / PROCESSING / PENDING_PICKUP）
     *      → REFUNDING → REFUNDED（约束 5.2）；COMPLETED 保持 COMPLETED；部分退款不改主状态
     *   ④ 写 order_status_log（status_type = AFTERSALE）
     *
     * 幂等（约束 5.3）：bizNo 非空时，同一支付单 + 同一 bizNo 已成功退款 →
     *   直接返回原退款单号，不再重复累加 refund_amount（防止重复审核重复扣款）。
     *
     * 注：orders.aftersale_status 聚合字段由 AftersaleService 负责写入，本方法不写。
     *
     * @param bizNo 业务幂等键（售后单号 aftersaleNo），可为空
     */
    @Transactional(rollbackFor = Exception.class)
    public String refund(Long orderId, BigDecimal refundAmount, String reason, String bizNo) {
        Orders order = ordersMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "订单不存在");
        }

        // 准入：v1.14 补 PENDING_PICKUP（待自提订单也应可退款）
        String os = order.getOrderStatus();
        boolean refundable = OrderStatusConstant.PAID.equals(os)
                || OrderStatusConstant.PROCESSING.equals(os)
                || OrderStatusConstant.PENDING_PICKUP.equals(os)
                || OrderStatusConstant.COMPLETED.equals(os);
        if (!refundable) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS,
                    "订单状态不允许退款：" + OrderStatusConstant.label(os));
        }
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "退款金额必须大于 0");
        }

        // 取支付单：同一订单可能存在多条（重复下单/历史脏数据），只取最新一条已成功/已退款的
        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>()
                        .eq(Payment::getOrderId, orderId)
                        .in(Payment::getPayStatus, "SUCCESS", "REFUNDED")
                        .orderByDesc(Payment::getCreateTime)
                        .last("LIMIT 1"));
        if (payment == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "支付单不存在");
        }

        // 幂等守卫：同一 bizNo 已成功退款 → 返回原退款单号
        if (bizNo != null && !bizNo.isBlank()) {
            PaymentCallbackLog done = callbackLogMapper.selectOne(
                    new LambdaQueryWrapper<PaymentCallbackLog>()
                            .eq(PaymentCallbackLog::getPaymentNo, payment.getPaymentNo())
                            .eq(PaymentCallbackLog::getCallbackType, "REFUND")
                            .eq(PaymentCallbackLog::getRequestBody, bizNo)
                            .eq(PaymentCallbackLog::getResult, "SUCCESS")
                            .orderByDesc(PaymentCallbackLog::getCreateTime)
                            .last("LIMIT 1"));
            if (done != null) {
                log.info("[退款] 幂等命中，跳过重复退款: bizNo={}, refundNo={}", bizNo, done.getResponseBody());
                return done.getResponseBody();
            }
        }

        BigDecimal alreadyRefunded = order.getRefundedAmount() == null ? BigDecimal.ZERO : order.getRefundedAmount();
        BigDecimal afterRefund = alreadyRefunded.add(refundAmount);
        if (afterRefund.compareTo(order.getPayAmount()) > 0) {
            throw new BusinessException(ErrorCode.REFUND_EXCEED, "退款金额超过实付金额");
        }

        // ① 支付单：REFUNDED + refund_amount 累加
        String refundNo = "REF" + IdUtil.getSnowflakeNextIdStr();
        payment.setPayStatus("REFUNDED");
        payment.setRefundAmount(
                (payment.getRefundAmount() == null ? BigDecimal.ZERO : payment.getRefundAmount()).add(refundAmount));
        payment.setRefundTime(LocalDateTime.now());
        paymentMapper.updateById(payment);

        // ② 订单退款金额累加
        order.setRefundedAmount(afterRefund);

        // ③ 订单主状态（约束 5.2）
        boolean fullRefund = afterRefund.compareTo(order.getPayAmount()) >= 0;
        boolean toTerminal = fullRefund
                && !OrderStatusConstant.COMPLETED.equals(os)
                && !OrderStatusConstant.isTerminal(os);
        if (toTerminal) {
            order.setOrderStatus(OrderStatusConstant.REFUNDED);
        }
        ordersMapper.updateById(order);

        // ④ 订单状态日志（全额退款记录 → REFUNDING → REFUNDED 完整轨迹）
        if (toTerminal) {
            writeOrderLog(orderId, order.getOrderNo(), os, OrderStatusConstant.REFUNDING,
                    "AFTERSALE", null, "发起退款：" + reason);
            writeOrderLog(orderId, order.getOrderNo(), OrderStatusConstant.REFUNDING, OrderStatusConstant.REFUNDED,
                    "AFTERSALE", null, "退款完成：" + refundNo);
        } else {
            writeOrderLog(orderId, order.getOrderNo(), os, os,
                    "AFTERSALE", null, "退款成功（部分退款/已完成订单）：" + reason);
        }

        writeCallbackLog(payment.getPaymentNo(), "REFUND", bizNo, refundNo, "SUCCESS", null);
        log.info("[退款] orderNo={}, amount={}, refundNo={}, fullRefund={}",
                order.getOrderNo(), refundAmount, refundNo, fullRefund);
        return refundNo;
    }

    /**
     * 超时释放定时任务（约束文档 6：二次校验订单状态防并发）
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void releaseTimeout() {
        List<Orders> expiredOrders = ordersMapper.selectList(
                new LambdaQueryWrapper<Orders>()
                        .eq(Orders::getOrderStatus, OrderStatusConstant.PENDING_PAY)
                        .lt(Orders::getExpireTime, LocalDateTime.now())
                        .last("LIMIT 100"));

        int success = 0, fail = 0;
        for (Orders order : expiredOrders) {
            try {
                // ✅ P2-1 修复（v1.19）：每条订单独立事务 REQUIRES_NEW
                //   原来：外层一个大事务 + try/catch，单条失败 catch 住不回滚整体，但前几条已经 commit 了
                //         而且 updateById + unlock 不在同一事务里，可能出现 CANCELLED 但库存没释放
                //   现在：self.releaseOneTimeout() 是 REQUIRES_NEW 独立事务，单条只影响自己
                self.releaseOneTimeout(order.getId());
                success++;
            } catch (Exception e) {
                fail++;
                log.error("[超时释放] 订单 {} 失败，已跳过", order.getOrderNo(), e);
            }
        }
        log.info("[超时释放] 本轮处理: 共 {} 条，成功 {}，失败 {}", expiredOrders.size(), success, fail);
    }

    /**
     * ✅ P2-1（v1.19）：单条超时关单独立事务
     * REQUIRES_NEW：每次调用都开新事务，外层调用者的事务挂起
     * — 保证每条订单的「改状态 CANCELLED + 释放库存 + 写日志」三者原子成功/失败
     * — 不会被外层 releaseTimeout 的异常影响
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void releaseOneTimeout(Long orderId) {
        Orders fresh = ordersMapper.selectById(orderId);
        if (fresh == null || !OrderStatusConstant.PENDING_PAY.equals(fresh.getOrderStatus())) {
            return; // 已被支付回调处理或不存在，幂等跳过
        }

        String oldStatus = fresh.getOrderStatus();
        fresh.setOrderStatus(OrderStatusConstant.CANCELLED);
        fresh.setCancelReason("超时未支付");
        fresh.setCancelTime(LocalDateTime.now());
        ordersMapper.updateById(fresh);

        // 释放库存（N+1 已修：批量查 stock）
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, fresh.getId()));
        if (!items.isEmpty()) {
            Set<Long> skuIds = items.stream().map(OrderItem::getSkuId).collect(Collectors.toSet());
            Map<Long, Stock> stockMap = stockService.batchGetBySkusAndCommunity(
                    skuIds, fresh.getCommunityId(), fresh.getPickupPointId());
            for (OrderItem oi : items) {
                Stock stock = stockMap.get(oi.getSkuId());
                if (stock != null) {
                    stockService.unlock(stock.getId(), oi.getQuantity(), fresh.getOrderNo(), "超时取消");
                }
            }
        }
        writeOrderLog(fresh.getId(), fresh.getOrderNo(), oldStatus, OrderStatusConstant.CANCELLED,
                "ORDER", null, "超时未支付自动取消");
        log.info("[超时释放] 订单 {} 已取消", fresh.getOrderNo());
    }

    private void writeCallbackLog(String paymentNo, String type, String req, String result, String err) {
        writeCallbackLog(paymentNo, type, req, null, result, err);
    }

    /**
     * 写支付回调日志（约束 5.3 幂等台账）
     * requestBody 存业务幂等键（如 aftersaleNo），responseBody 存退款单号，供重复调用时回放。
     */
    private void writeCallbackLog(String paymentNo, String type, String req, String resp, String result, String err) {
        PaymentCallbackLog entry = new PaymentCallbackLog();
        entry.setPaymentNo(paymentNo);
        entry.setCallbackType(type);
        entry.setRequestBody(req);
        entry.setResponseBody(resp);
        entry.setResult(result);
        entry.setErrorMsg(err);
        callbackLogMapper.insert(entry);
    }

    private void writeOrderLog(Long orderId, String orderNo, String from, String to, String type, Long userId, String reason) {
        com.project.groupbuy.modules.order.entity.OrderStatusLog l = new com.project.groupbuy.modules.order.entity.OrderStatusLog();
        l.setOrderId(orderId);
        l.setOrderNo(orderNo);
        l.setFromStatus(from);
        l.setToStatus(to);
        l.setStatusType(type);
        l.setOperatorId(userId);
        l.setReason(reason);
        orderStatusLogMapper.insert(l);
    }

    public Payment getPayment(String paymentNo) {
        Payment p = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>().eq(Payment::getPaymentNo, paymentNo));
        if (p == null) throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "支付单不存在");
        return p;
    }
}
