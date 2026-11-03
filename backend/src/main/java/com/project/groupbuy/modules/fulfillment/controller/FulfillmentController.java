package com.project.groupbuy.modules.fulfillment.controller;

import com.project.groupbuy.common.result.R;
import com.project.groupbuy.modules.fulfillment.entity.Fulfillment;
import com.project.groupbuy.modules.fulfillment.service.FulfillmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 履约控制器（约束文档 5.4 履约状态机）
 */
@Slf4j
@RestController
@RequestMapping("/fulfillments")
@RequiredArgsConstructor
public class FulfillmentController {

    private final FulfillmentService fulfillmentService;

    /**
     * 商家分配履约（v1.13：PAID → PROCESSING，不再直通 PENDING_PICKUP）
     */
    @PostMapping("/assign")
    public R<Fulfillment> assign(@RequestParam Long orderId,
                                  @RequestParam String fulfillmentType,
                                  @RequestParam(required = false) Long pickupPointId) {
        return R.ok(fulfillmentService.assign(orderId, fulfillmentType, pickupPointId));
    }

    /**
     * 商家标记自提订单「已送到自提点」（PROCESSING → PENDING_PICKUP）
     * 消费者端此时才会看到自提码 banner
     */
    @PostMapping("/{id}/mark-picked-up")
    public R<Fulfillment> markPickedUp(@PathVariable Long id,
                                         @RequestParam(required = false) String reason) {
        return R.ok(fulfillmentService.markPickedUp(id, reason));
    }

    /**
     * 商家标记配送订单「已送达」（PROCESSING → COMPLETED）
     */
    @PostMapping("/{id}/mark-delivered")
    public R<Fulfillment> markDelivered(@PathVariable Long id,
                                         @RequestParam(required = false) String reason) {
        return R.ok(fulfillmentService.markDelivered(id, reason));
    }

    /**
     * 状态流转（商家操作）
     */
    @PostMapping("/{id}/transition")
    public R<Fulfillment> transition(@PathVariable Long id,
                                       @RequestParam String toStatus,
                                       @RequestParam(required = false) String reason) {
        return R.ok(fulfillmentService.transition(id, toStatus, reason));
    }

    /**
     * 消费者扫码自提确认（PICKED_UP → COMPLETED）
     */
    @PostMapping("/{id}/confirm-pickup")
    public R<Fulfillment> confirmPickup(@PathVariable Long id) {
        return R.ok(fulfillmentService.confirmPickup(id));
    }

    /**
     * 商家标记异常
     */
    @PostMapping("/{id}/exception")
    public R<Fulfillment> markException(@PathVariable Long id, @RequestParam String reason) {
        return R.ok(fulfillmentService.markException(id, reason));
    }

    /**
     * 按当前登录角色查询履约列表（MERCHANT / PICKUP_STAFF / CONSUMER / ADMIN 自动隔离）
     */
    @GetMapping("/mine")
    public R<List<Fulfillment>> mine(@RequestParam(required = false) String status,
                                      @RequestParam(required = false) String fulfillmentType) {
        return R.ok(fulfillmentService.listMine(status, fulfillmentType));
    }

    /**
     * 履约详情
     */
    @GetMapping("/{id:\\d+}")
    public R<Fulfillment> get(@PathVariable Long id) {
        return R.ok(fulfillmentService.get(id));
    }
}
