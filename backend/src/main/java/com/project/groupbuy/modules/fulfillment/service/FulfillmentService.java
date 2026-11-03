package com.project.groupbuy.modules.fulfillment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.project.groupbuy.common.context.SecurityContext;
import com.project.groupbuy.common.constant.OrderStatusConstant;
import com.project.groupbuy.common.constant.FulfillmentStatusConstant;
import com.project.groupbuy.common.constant.UserTypeConstant;
import com.project.groupbuy.common.exception.BusinessException;
import com.project.groupbuy.common.result.ErrorCode;
import com.project.groupbuy.modules.fulfillment.entity.Fulfillment;
import com.project.groupbuy.modules.fulfillment.entity.FulfillmentRecord;
import com.project.groupbuy.modules.fulfillment.mapper.FulfillmentMapper;
import com.project.groupbuy.modules.fulfillment.mapper.FulfillmentRecordMapper;
import com.project.groupbuy.modules.order.entity.OrderStatusLog;
import com.project.groupbuy.modules.order.entity.Orders;
import com.project.groupbuy.modules.order.entity.OrderItem;
import com.project.groupbuy.modules.order.mapper.OrderItemMapper;
import com.project.groupbuy.modules.order.mapper.OrderStatusLogMapper;
import com.project.groupbuy.modules.order.mapper.OrdersMapper;
import com.project.groupbuy.modules.community.mapper.PickupPointMapper;
import com.project.groupbuy.modules.auth.entity.User;
import com.project.groupbuy.modules.auth.mapper.UserMapper;
import com.project.groupbuy.modules.supply.entity.Stock;
import com.project.groupbuy.modules.supply.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 履约服务（约束文档 5.4 履约状态机）
 *
 * 履约状态机：
 *   PENDING_ASSIGN → PENDING_PICKUP / DELIVERING → DELIVERED / PICKED_UP → COMPLETED
 *   任意节点 → EXCEPTION
 *
 * 驱动订单状态：PAID → FULFILLING → COMPLETED
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FulfillmentService {

    private final FulfillmentMapper fulfillmentMapper;
    private final FulfillmentRecordMapper fulfillmentRecordMapper;
    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStatusLogMapper orderStatusLogMapper;
    private final PickupPointMapper pickupPointMapper;
    private final UserMapper userMapper;
    private final StockService stockService;

    /**
     * 商家分配履约（PAID → FULFILLING + PENDING_ASSIGN → PENDING_PICKUP/DELIVERING）
     */
    @Transactional(rollbackFor = Exception.class)
    public Fulfillment assign(Long orderId, String fulfillmentType, Long pickupPointId) {
        Orders order = ordersMapper.selectById(orderId);
        if (order == null) throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "订单不存在");
        if (!OrderStatusConstant.PAID.equals(order.getOrderStatus())) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS, "只有已支付订单可分配履约");
        }

        // 创建履约记录
        Fulfillment f = new Fulfillment();
        f.setOrderId(orderId);
        f.setOrderNo(order.getOrderNo());
        f.setFulfillmentType(fulfillmentType);
        f.setPickupPointId(pickupPointId);
        f.setReceiverName(order.getReceiverName());
        f.setReceiverPhone(order.getReceiverPhone());
        f.setReceiverAddress(order.getReceiverAddress());
        f.setFulfillmentStatus(FulfillmentStatusConstant.PENDING_ASSIGN);
        fulfillmentMapper.insert(f);

        // 立即流转到 PROCESSING（v1.13 修复：自提/配送统一先到 PROCESSING，跳过直通 PENDING_PICKUP 的跳级）
        // 商家后续：
        //   自提 → markPickedUp() 推 PROCESSING → PENDING_PICKUP（货送到自提点）
        //   配送 → markDelivered() 推 PROCESSING → COMPLETED
        String target = FulfillmentStatusConstant.DELIVERING;
        return transition(f.getId(), target, null);
    }

    /**
     * 商家标记自提订单「已送到自提点」（约束文档 v1.13）
     * PROCESSING → PENDING_PICKUP（订单状态同步）
     * 消费者端此时才会看到自提码 banner
     */
    @Transactional(rollbackFor = Exception.class)
    public Fulfillment markPickedUp(Long fulfillmentId, String reason) {
        Fulfillment f = fulfillmentMapper.selectById(fulfillmentId);
        if (f == null) throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "履约记录不存在");
        if (!FulfillmentStatusConstant.TYPE_PICKUP.equals(f.getFulfillmentType())) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS, "非自提订单不能标记已到自提点");
        }
        return transition(fulfillmentId, OrderStatusConstant.PENDING_PICKUP, reason != null ? reason : "商家标记已送到自提点");
    }

    /**
     * 商家标记配送订单「已送达」（约束文档 v1.13）
     * PROCESSING → COMPLETED（订单状态同步 + finish_time 写入）
     */
    @Transactional(rollbackFor = Exception.class)
    public Fulfillment markDelivered(Long fulfillmentId, String reason) {
        Fulfillment f = fulfillmentMapper.selectById(fulfillmentId);
        if (f == null) throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "履约记录不存在");
        return transition(fulfillmentId, FulfillmentStatusConstant.COMPLETED, reason != null ? reason : "商家标记已送达");
    }

    /**
     * 状态流转核心方法（约束文档 5.4：每次变更必须写日志 + 同步订单状态）
     */
    @Transactional(rollbackFor = Exception.class)
    public Fulfillment transition(Long fulfillmentId, String toStatus, String reason) {
        Fulfillment f = fulfillmentMapper.selectById(fulfillmentId);
        if (f == null) throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "履约记录不存在");

        // v1.14 终态守卫（约束 5.2）：订单进入终态（CANCELLED / REFUNDING / REFUNDED）后禁止继续履约
        Orders order = ordersMapper.selectById(f.getOrderId());
        if (order != null && OrderStatusConstant.isTerminal(order.getOrderStatus())) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS,
                    "订单已进入终态（" + OrderStatusConstant.label(order.getOrderStatus()) + "），不允许继续履约");
        }

        String fromStatus = f.getFulfillmentStatus();
        if (fromStatus.equals(toStatus)) return f;

        // 校验合法流转
        validateTransition(fromStatus, toStatus);

        f.setFulfillmentStatus(toStatus);
        f.setAssignTime(OrderStatusConstant.PENDING_PICKUP.equals(toStatus) || FulfillmentStatusConstant.DELIVERING.equals(toStatus)
                ? LocalDateTime.now() : f.getAssignTime());
        f.setPickupTime(FulfillmentStatusConstant.PICKED_UP.equals(toStatus) ? LocalDateTime.now() : f.getPickupTime());
        // 【补丁】商家直接 DELIVERING → COMPLETED（跳过 DELIVERED）时也要写 delivery_time
        LocalDateTime now = LocalDateTime.now();
        f.setDeliveryTime(FulfillmentStatusConstant.DELIVERED.equals(toStatus) || FulfillmentStatusConstant.COMPLETED.equals(toStatus)
                ? (f.getDeliveryTime() == null ? now : f.getDeliveryTime()) : f.getDeliveryTime());
        f.setCompleteTime(FulfillmentStatusConstant.COMPLETED.equals(toStatus) ? now : f.getCompleteTime());
        f.setExceptionReason(FulfillmentStatusConstant.EXCEPTION.equals(toStatus) ? reason : f.getExceptionReason());
        fulfillmentMapper.updateById(f);

        // 写履约日志（约束 10：操作日志必须记录真实操作人）
        writeRecord(fulfillmentId, fromStatus, toStatus, reason);

        // ✅ P2-2 修复（v1.19）：复用 L123 已查的 order，去掉重复 selectById
        // 两次查询之间只有 fulfillment.updateById（不影响 orders 表），order 数据没变
        String curOrderStatus = order.getOrderStatus();
        String newOrderStatus = curOrderStatus;

        if (OrderStatusConstant.PENDING_PICKUP.equals(toStatus)) {
            // ✨ 自提支线：货到自提点 → 订单状态 = PENDING_PICKUP（用户能看到货已到）
            if (!OrderStatusConstant.PENDING_PICKUP.equals(curOrderStatus)) {
                newOrderStatus = OrderStatusConstant.PENDING_PICKUP;
                order.setFulfillmentStatus(OrderStatusConstant.PENDING_PICKUP);
            }
        } else if (FulfillmentStatusConstant.DELIVERING.equals(toStatus)) {
            // 配送支线：配送中 → FULFILLING
            if (!OrderStatusConstant.PROCESSING.equals(curOrderStatus)) {
                newOrderStatus = OrderStatusConstant.PROCESSING;
                order.setFulfillmentStatus(FulfillmentStatusConstant.DELIVERING);
            }
        } else if (FulfillmentStatusConstant.DELIVERED.equals(toStatus)) {
            // 配送：已送达 → 保持 FULFILLING（等用户确认收货后再 COMPLETED）
            order.setFulfillmentStatus(FulfillmentStatusConstant.DELIVERED);
        } else if (FulfillmentStatusConstant.PICKED_UP.equals(toStatus)) {
            // ✨ 自提：已核销 → 订单直接 COMPLETED
            newOrderStatus = FulfillmentStatusConstant.COMPLETED;
            order.setFulfillmentStatus(FulfillmentStatusConstant.PICKED_UP);
            order.setFinishTime(LocalDateTime.now());
        } else if (FulfillmentStatusConstant.COMPLETED.equals(toStatus)) {
            newOrderStatus = FulfillmentStatusConstant.COMPLETED;
            // 【补丁】根据履约类型区分 fulfillment_status（自提→PICKED_UP，配送→DELIVERED）
            order.setFulfillmentStatus(FulfillmentStatusConstant.TYPE_PICKUP.equals(f.getFulfillmentType())
                    ? FulfillmentStatusConstant.PICKED_UP : FulfillmentStatusConstant.DELIVERED);
            order.setFinishTime(LocalDateTime.now());
        } else if (FulfillmentStatusConstant.EXCEPTION.equals(toStatus)) {
            order.setFulfillmentStatus(FulfillmentStatusConstant.EXCEPTION);
        }

        if (!curOrderStatus.equals(newOrderStatus)) {
            String old = order.getOrderStatus();
            order.setOrderStatus(newOrderStatus);
            ordersMapper.updateById(order);
            writeOrderLog(order.getId(), order.getOrderNo(), old, newOrderStatus,
                    "FULFILLMENT", SecurityContext.getUserId(),
                    "履约状态变更: " + fromStatus + " → " + toStatus);

            // 【约束 §6 库存生命周期】订单首次进入 COMPLETED → 预占转实扣
            // locked_qty -= qty, total_qty -= qty（幂等：curOrderStatus != COMPLETED 保证只触发一次）
            if (OrderStatusConstant.COMPLETED.equals(newOrderStatus)
                    && !OrderStatusConstant.COMPLETED.equals(curOrderStatus)) {
                List<OrderItem> items = orderItemMapper.selectList(
                        new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
                if (!items.isEmpty()) {
                    // ✅ N+1 修复（v1.19）：先 collect skuIds → 一次 batchGetBySkusAndCommunity
                    Set<Long> skuIds = items.stream().map(OrderItem::getSkuId).collect(Collectors.toSet());
                    Map<Long, Stock> stockMap = stockService.batchGetBySkusAndCommunity(
                            skuIds, order.getCommunityId(), order.getPickupPointId());
                    for (OrderItem oi : items) {
                        Stock stock = stockMap.get(oi.getSkuId());
                        if (stock != null) {
                            stockService.deduct(stock.getId(), oi.getQuantity(), order.getOrderNo());
                        }
                    }
                }
                log.info("约束§6 库存实扣完成：orderNo={} 共 {} 个 item 预占转实扣", order.getOrderNo(), items.size());
            }
        } else if (order.getFulfillmentStatus() != null && !order.getFulfillmentStatus().equals(toStatus)) {
            // fulfillment_status 变了但 order_status 没变，也要更新 orders.fulfillment_status 字段
            ordersMapper.updateById(order);
        }

        return f;
    }

    /**
     * 消费者扫码确认自提（PICKED_UP → COMPLETED）
     */
    @Transactional(rollbackFor = Exception.class)
    public Fulfillment confirmPickup(Long fulfillmentId) {
        return transition(fulfillmentId, FulfillmentStatusConstant.COMPLETED, "消费者扫码自提确认");
    }

    /**
     * 商家标记履约异常
     */
    @Transactional(rollbackFor = Exception.class)
    public Fulfillment markException(Long fulfillmentId, String reason) {
        return transition(fulfillmentId, FulfillmentStatusConstant.EXCEPTION, reason);
    }

    /**
     * 通用履约列表（数据隔离：按当前登录角色过滤）
     * - MERCHANT:  看自己 merchant_id 下的订单
     * - PICKUP_STAFF: 只看自己自提点的 PENDING_PICKUP 订单（等核销的）
     * - CONSUMER/ADMIN: 各自隔离（略，当前只有 MERCHANT + PICKUP_STAFF 用）
     */
    public List<Fulfillment> listMine(String status, String fulfillmentType) {
        String userType = SecurityContext.getUserType();
        Long userId = SecurityContext.getUserId();

        // 先按角色查 orders 表拿到 order_id 集合
        List<Long> orderIds;
        LambdaQueryWrapper<Orders> ow = new LambdaQueryWrapper<>();

        if (UserTypeConstant.PICKUP_STAFF.equals(userType)) {
            // 核销员：通过 pickup_point.staff_user_id 找到自己的自提点 → 该自提点的订单
            com.project.groupbuy.modules.community.entity.PickupPoint pp =
                    pickupPointMapper.selectOne(new LambdaQueryWrapper<com.project.groupbuy.modules.community.entity.PickupPoint>()
                            .eq(com.project.groupbuy.modules.community.entity.PickupPoint::getStaffUserId, userId).last("LIMIT 1"));
            if (pp == null) return Collections.emptyList();
            ow.eq(Orders::getPickupPointId, pp.getId());
            // 默认只看已到自提点（等核销的），除非显式指定
            if (status == null || status.isEmpty()) {
                ow.in(Orders::getOrderStatus, OrderStatusConstant.PENDING_PICKUP, FulfillmentStatusConstant.COMPLETED);
            }
        } else if (UserTypeConstant.MERCHANT.equals(userType)) {
            // 【约束 §8 重构】用 item 归属子查询代替 orders.merchant_id
            // —— 兼容 V14 把历史脏订单 merchant_id 置 NULL，以及防止未来 orders.merchant_id 语义漂移
            Long mid = SecurityContext.getMerchantId();
            ow.inSql(Orders::getId,
                    "SELECT DISTINCT oi.order_id FROM order_item oi" +
                    " JOIN product_sku sk ON sk.id = oi.sku_id" +
                    " JOIN product p ON p.id = sk.product_id" +
                    " WHERE oi.is_deleted = 0 AND sk.is_deleted = 0 AND p.is_deleted = 0" +
                    " AND p.merchant_id = " + mid);
        } else if (UserTypeConstant.CONSUMER.equals(userType)) {
            ow.eq(Orders::getUserId, userId);
        } else {
            // ADMIN / SUPER_ADMIN 看全部
            orderIds = null; // 后面不走 orderIds 过滤
            LambdaQueryWrapper<Fulfillment> w2 = new LambdaQueryWrapper<>();
            w2.eq(status != null && !status.isEmpty(), Fulfillment::getFulfillmentStatus, status);
            w2.eq(fulfillmentType != null && !fulfillmentType.isEmpty(), Fulfillment::getFulfillmentType, fulfillmentType);
            w2.orderByDesc(Fulfillment::getCreateTime);
            List<Fulfillment> adminList = fulfillmentMapper.selectList(w2);
            fillSnapshot(adminList);
            return adminList;
        }

        ow.select(Orders::getId);
        orderIds = ordersMapper.selectList(ow).stream().map(Orders::getId).collect(java.util.stream.Collectors.toList());
        if (orderIds.isEmpty()) return Collections.emptyList();

        LambdaQueryWrapper<Fulfillment> w = new LambdaQueryWrapper<>();
        w.in(Fulfillment::getOrderId, orderIds);
        w.eq(status != null && !status.isEmpty(), Fulfillment::getFulfillmentStatus, status);
        w.eq(fulfillmentType != null && !fulfillmentType.isEmpty(), Fulfillment::getFulfillmentType, fulfillmentType);
        w.orderByDesc(Fulfillment::getCreateTime);
        List<Fulfillment> list = fulfillmentMapper.selectList(w);
        fillSnapshot(list);
        return list;
    }

    /**
     * 批量填充 fulfillment 列表的订单快照（pickup_code / receiver / 商品明细）
     * 约束 7.2.3：前后端字段必须对齐，核销员/商家端需要完整订单视图
     */
    private void fillSnapshot(List<Fulfillment> list) {
        if (list == null || list.isEmpty()) return;
        // 查所有 order_id 对应的 orders（pickup_code / receiver）
        List<Long> oids = list.stream().map(Fulfillment::getOrderId).distinct().collect(java.util.stream.Collectors.toList());
        Map<Long, Orders> orderMap = ordersMapper.selectBatchIds(oids).stream()
                .collect(java.util.stream.Collectors.toMap(Orders::getId, o -> o));
        // 查所有 order_item
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, oids));
        Map<Long, List<OrderItem>> itemMap = items.stream()
                .collect(java.util.stream.Collectors.groupingBy(OrderItem::getOrderId));
        // 填充
        for (Fulfillment f : list) {
            Orders o = orderMap.get(f.getOrderId());
            if (o != null) {
                f.setPickupCode(o.getPickupCode());
                f.setOrderStatus(o.getOrderStatus());
                // receiver_name/phone 优先用 orders 表的（自提时 fulfillment 表没填）
                if (f.getReceiverName() == null || f.getReceiverName().isEmpty()) f.setReceiverName(o.getReceiverName());
                if (f.getReceiverPhone() == null || f.getReceiverPhone().isEmpty()) f.setReceiverPhone(o.getReceiverPhone());
            }
            List<OrderItem> its = itemMap.getOrDefault(f.getOrderId(), Collections.emptyList());
            List<Map<String, Object>> itemMaps = its.stream().map(it -> {
                Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("productName", it.getProductName());
                m.put("skuName", it.getSkuName());
                m.put("spec", it.getSpec());
                m.put("quantity", it.getQuantity());
                m.put("price", it.getPrice());
                m.put("image", it.getImage());
                return m;
            }).collect(java.util.stream.Collectors.toList());
            f.setItems(itemMaps);
            int totalQty = its.stream().mapToInt(OrderItem::getQuantity).sum();
            f.setTotalQty(totalQty);
        }
        // V1.8 审计痕迹：批量回填最近一次操作人（从 fulfillment_record JOIN user）
        fillLastOperator(list);
    }

    /**
     * 批量回填最近操作人信息（约束 10 日志与审计：核销者必须可追溯）
     * 每个 fulfillment 对应 fulfillment_record 里 create_time 最新的那条
     */
    private void fillLastOperator(List<Fulfillment> list) {
        if (list == null || list.isEmpty()) return;
        List<Long> fids = list.stream().map(Fulfillment::getId).collect(java.util.stream.Collectors.toList());
        List<FulfillmentRecord> all = fulfillmentRecordMapper.selectList(
                new LambdaQueryWrapper<FulfillmentRecord>().in(FulfillmentRecord::getFulfillmentId, fids)
                        .orderByDesc(FulfillmentRecord::getCreateTime));
        // 按 fulfillment_id 分组取第一条（即最新）
        Map<Long, FulfillmentRecord> latestMap = new java.util.HashMap<>();
        List<Long> opIds = new java.util.ArrayList<>();
        for (FulfillmentRecord r : all) {
            latestMap.putIfAbsent(r.getFulfillmentId(), r);
            if (r.getOperatorId() != null) opIds.add(r.getOperatorId());
        }
        // 批量查用户昵称
        Map<Long, User> userMap = opIds.isEmpty() ? Collections.emptyMap() :
                userMapper.selectBatchIds(opIds).stream().collect(java.util.stream.Collectors.toMap(User::getId, u -> u));
        for (Fulfillment f : list) {
            FulfillmentRecord r = latestMap.get(f.getId());
            if (r != null) {
                f.setLastOperatorRole(r.getOperatorRole());
                f.setLastOpTime(r.getCreateTime());
                if (r.getOperatorId() != null) {
                    User u = userMap.get(r.getOperatorId());
                    f.setLastOperatorName(u != null ? u.getNickname() : "用户#" + r.getOperatorId());
                }
            }
        }
    }

    /**
     * 查询履约详情
     */
    public Fulfillment get(Long id) {
        Fulfillment f = fulfillmentMapper.selectById(id);
        if (f == null) throw new BusinessException(ErrorCode.DATA_NOT_FOUND);
        return f;
    }

    private void validateTransition(String from, String to) {
        // 合法流转表（约束文档 5.4）
        boolean valid = switch (from) {
            case FulfillmentStatusConstant.PENDING_ASSIGN -> to.equals(OrderStatusConstant.PENDING_PICKUP) || to.equals(FulfillmentStatusConstant.DELIVERING) || to.equals(FulfillmentStatusConstant.EXCEPTION);
            case OrderStatusConstant.PENDING_PICKUP -> to.equals(FulfillmentStatusConstant.PICKED_UP) || to.equals(FulfillmentStatusConstant.COMPLETED) || to.equals(FulfillmentStatusConstant.EXCEPTION); // v1.6: 支持 confirmPickup 直达 COMPLETED
            case FulfillmentStatusConstant.DELIVERING -> to.equals(FulfillmentStatusConstant.DELIVERED) || to.equals(FulfillmentStatusConstant.COMPLETED) || to.equals(FulfillmentStatusConstant.EXCEPTION); // v1.15: 商家标记已送达 → 直达 COMPLETED（跳过 DELIVERED）
            case FulfillmentStatusConstant.DELIVERED, FulfillmentStatusConstant.PICKED_UP -> to.equals(FulfillmentStatusConstant.COMPLETED) || to.equals(FulfillmentStatusConstant.EXCEPTION);
            default -> to.equals(FulfillmentStatusConstant.EXCEPTION); // EXCEPTION / COMPLETED 只能变 EXCEPTION（异常回退）
        };
        if (!valid) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS,
                    String.format("履约状态 %s → %s 不合法", from, to));
        }
    }

    /**
     * 写履约操作日志（约束 10：操作人必须从 SecurityContext 取真实登录身份）
     * 核销员 pickup01 核销 → operatorId=7, operatorRole=UserTypeConstant.PICKUP_STAFF
     * 商家 merchant01 操作 → operatorId=2, operatorRole=UserTypeConstant.MERCHANT
     * ADMIN 操作          → operatorId=1, operatorRole=UserTypeConstant.ADMIN
     */
    private void writeRecord(Long fid, String from, String to, String remark) {
        FulfillmentRecord r = new FulfillmentRecord();
        r.setFulfillmentId(fid);
        r.setFromStatus(from);
        r.setToStatus(to);
        // 自动从 ThreadLocal SecurityContext 取当前登录者身份，禁止硬编码
        r.setOperatorId(SecurityContext.getUserId());
        r.setOperatorRole(SecurityContext.getUserType());
        r.setRemark(remark);
        fulfillmentRecordMapper.insert(r);
    }

    private void writeOrderLog(Long oid, String orderNo, String from, String to, String type, Long userId, String reason) {
        OrderStatusLog l = new OrderStatusLog();
        l.setOrderId(oid);
        l.setOrderNo(orderNo);
        l.setFromStatus(from);
        l.setToStatus(to);
        l.setStatusType(type);
        l.setOperatorId(userId);
        l.setReason(reason);
        orderStatusLogMapper.insert(l);
    }
}
