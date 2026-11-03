package com.project.groupbuy.modules.payment.controller;

import com.project.groupbuy.common.result.R;
import com.project.groupbuy.modules.payment.entity.Payment;
import com.project.groupbuy.modules.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 支付控制器（约束文档 5.3 模拟支付 + 幂等回调）
 */
@Slf4j
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 创建支付单
     */
    @PostMapping
    public R<Payment> create(@RequestParam Long orderId) {
        return R.ok(paymentService.create(orderId));
    }

    /**
     * 模拟支付回调（幂等）
     * 前端模拟：点击"模拟支付成功"→调此接口
     */
    @PostMapping("/callback/{paymentNo}")
    public R<Void> callback(@PathVariable String paymentNo) {
        paymentService.mockCallback(paymentNo);
        return R.ok();
    }

    /**
     * 查询支付单
     */
    @GetMapping("/{paymentNo}")
    public R<Payment> get(@PathVariable String paymentNo) {
        return R.ok(paymentService.getPayment(paymentNo));
    }
}
