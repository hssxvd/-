package com.project.groupbuy.modules.admin.service;

import lombok.extern.slf4j.Slf4j;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.project.groupbuy.common.aspect.OpLog;
import com.project.groupbuy.common.context.SecurityContext;
import com.project.groupbuy.common.exception.BusinessException;
import com.project.groupbuy.common.result.ErrorCode;
import com.project.groupbuy.common.constant.UserTypeConstant;
import com.project.groupbuy.modules.community.entity.Community;
import com.project.groupbuy.modules.community.mapper.CommunityMapper;
import com.project.groupbuy.modules.product.entity.Merchant;
import com.project.groupbuy.modules.product.mapper.MerchantMapper;
import com.project.groupbuy.modules.auth.entity.User;
import com.project.groupbuy.modules.product.entity.Product;
import com.project.groupbuy.modules.product.mapper.ProductMapper;
import com.project.groupbuy.common.constant.OrderStatusConstant;
import java.math.RoundingMode;
import com.project.groupbuy.modules.auth.mapper.UserMapper;
import com.project.groupbuy.modules.order.entity.Orders;
import com.project.groupbuy.modules.order.entity.OrderItem;
import com.project.groupbuy.modules.order.entity.OrderStatusLog;
import com.project.groupbuy.modules.order.mapper.OrdersMapper;
import com.project.groupbuy.modules.order.mapper.OrderItemMapper;
import com.project.groupbuy.modules.order.mapper.OrderStatusLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理员端 · 全局订单查询 + 管理
 * 约束文档 §23 管理员全局审计 / §5.2 订单状态机 / L115 数据范围
 */
@Slf4j
@Service
public class AdminOrderService {

    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStatusLogMapper orderStatusLogMapper;
    private final MerchantMapper merchantMapper;
    private final CommunityMapper communityMapper;
    private final UserMapper userMapper;
    private final ProductMapper productMapper;

    public AdminOrderService(OrdersMapper ordersMapper, OrderItemMapper orderItemMapper,
                             OrderStatusLogMapper orderStatusLogMapper, MerchantMapper merchantMapper,
                             CommunityMapper communityMapper, UserMapper userMapper,
                             ProductMapper productMapper) {
        this.ordersMapper = ordersMapper;
        this.orderItemMapper = orderItemMapper;
        this.orderStatusLogMapper = orderStatusLogMapper;
        this.merchantMapper = merchantMapper;
        this.communityMapper = communityMapper;
        this.userMapper = userMapper;
        this.productMapper = productMapper;
    }

    /** 全局分页查询（超管无隔离，社区管理员只看自己 communityId） */
    public IPage<Map<String, Object>> page(int pageNum, int pageSize,
                                           String orderStatus, String payStatus,
                                           String fulfillmentStatus, String aftersaleStatus,
                                           String orderNo, Long userId, Long merchantId, Long communityId,
                                           BigDecimal minAmount, BigDecimal maxAmount,
                                           LocalDateTime startTime, LocalDateTime endTime) {
        Page<Orders> page = new Page<>(Math.max(1, pageNum), Math.min(100, pageSize));
        LambdaQueryWrapper<Orders> w = new LambdaQueryWrapper<>();

        // 约束 L115 数据范围：社区管理员（非超管）只能看自己的社区
        String userType = SecurityContext.getUserType();
        if (!UserTypeConstant.isAdmin(userType)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理员可查看全局订单");
        }
        if ("COMMUNITY_ADMIN".equals(userType)) {
            Long cid = null;
            User u = userMapper.selectById(SecurityContext.getUserId());
            if (u != null && u.getCommunityId() != null) cid = u.getCommunityId();
            if (cid != null) w.eq(Orders::getCommunityId, cid);
            else w.eq(Orders::getUserId, -1L); // 兜底：没绑定社区的看不到任何订单
        }

        if (StringUtils.hasText(orderStatus))        w.eq(Orders::getOrderStatus, orderStatus);
        if (StringUtils.hasText(payStatus))          w.eq(Orders::getPayStatus, payStatus);
        if (StringUtils.hasText(fulfillmentStatus))  w.eq(Orders::getFulfillmentStatus, fulfillmentStatus);
        if (StringUtils.hasText(aftersaleStatus))    w.eq(Orders::getAftersaleStatus, aftersaleStatus);
        if (StringUtils.hasText(orderNo))            w.like(Orders::getOrderNo, orderNo);
        if (userId != null)                          w.eq(Orders::getUserId, userId);
        if (merchantId != null)                      w.eq(Orders::getMerchantId, merchantId);
        if (communityId != null)                     w.eq(Orders::getCommunityId, communityId);
        if (minAmount != null)                       w.ge(Orders::getPayAmount, minAmount);
        if (maxAmount != null)                       w.le(Orders::getPayAmount, maxAmount);
        if (startTime != null)                       w.ge(Orders::getCreateTime, startTime);
        if (endTime != null)                         w.le(Orders::getCreateTime, endTime);
        w.orderByDesc(Orders::getCreateTime);

        IPage<Orders> orderPage = ordersMapper.selectPage(page, w);

        // 聚合 join：merchantName / communityName / consumerNickname
        List<Orders> records = orderPage.getRecords();
        Set<Long> userIds = records.stream().map(Orders::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> merchantIds = records.stream().map(Orders::getMerchantId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> communityIds = records.stream().map(Orders::getCommunityId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, String> userMap = userIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(User::getId, u -> u.getNickname() != null ? u.getNickname() : u.getUsername(), (a, b) -> a));
        Map<Long, String> merchantMap = merchantIds.isEmpty() ? Map.of() :
                merchantMapper.selectBatchIds(merchantIds).stream().collect(Collectors.toMap(Merchant::getId, Merchant::getMerchantName, (a, b) -> a));
        Map<Long, String> communityMap = communityIds.isEmpty() ? Map.of() :
                communityMapper.selectBatchIds(communityIds).stream().collect(Collectors.toMap(Community::getId, Community::getCommunityName, (a, b) -> a));

        Page<Map<String, Object>> result = new Page<>(orderPage.getCurrent(), orderPage.getSize(), orderPage.getTotal());

        // 批量取首图（join product.mainImage，让列表图与商品管理实时统一）
        Map<Long, String> firstImageMap = loadFirstItemImageMap(records);

        result.setRecords(records.stream().map(o -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("order", o);
            m.put("userNickname", userMap.get(o.getUserId()));
            m.put("merchantName", merchantMap.get(o.getMerchantId()));
            m.put("communityName", communityMap.get(o.getCommunityId()));
            m.put("firstItemImage", firstImageMap.get(o.getId()));
            return m;
        }).toList());
        return result;
    }

    /** 订单详情（聚合 items + statusLog + 3 表 join） */
    public Map<String, Object> detail(Long id) {
        Orders order = ordersMapper.selectById(id);
        if (order == null) throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "订单不存在");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("order", order);

        // Join 名称
        if (order.getUserId() != null) {
            User u = userMapper.selectById(order.getUserId());
            if (u != null) result.put("userNickname", u.getNickname() != null ? u.getNickname() : u.getUsername());
        }
        if (order.getMerchantId() != null) {
            Merchant m = merchantMapper.selectById(order.getMerchantId());
            if (m != null) result.put("merchantName", m.getMerchantName());
        }
        if (order.getCommunityId() != null) {
            Community c = communityMapper.selectById(order.getCommunityId());
            if (c != null) result.put("communityName", c.getCommunityName());
        }

        // 聚合 OrderItem 商品快照
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, id));
        overrideImagesWithLiveProduct(items);
        result.put("items", items);

        // 聚合状态变更链
        List<OrderStatusLog> logs = orderStatusLogMapper.selectList(
                new LambdaQueryWrapper<OrderStatusLog>()
                        .eq(OrderStatusLog::getOrderNo, order.getOrderNo())
                        .orderByAsc(OrderStatusLog::getCreateTime));
        result.put("logs", logs);

        return result;
    }

    /** 管理员强制取消订单（约束 §5.2 合法状态转换） */
    @Transactional(rollbackFor = Exception.class)
    @OpLog(module = "订单", operationType = "AUDIT", targetType = "Orders")
    public void adminCancel(Long id, String reason) {
        Orders order = ordersMapper.selectById(id);
        if (order == null) throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "订单不存在");
        // 允许取消：PENDING_PAY / PAID（履约前） / PENDING_CONFIRM
        Set<String> allowed = Set.of("PENDING_CONFIRM", "PENDING_PAY", "PAID");
        if (!allowed.contains(order.getOrderStatus())) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS, "当前状态不允许取消：" + order.getOrderStatus());
        }
        String fromStatus = order.getOrderStatus();
        order.setOrderStatus("CANCELLED");
        order.setCancelReason(reason);
        order.setCancelTime(LocalDateTime.now());
        ordersMapper.updateById(order);

        // 写状态变更审计链（约束 §10 + 5.2）
        OrderStatusLog log = new OrderStatusLog();
        log.setOrderId(id);
        log.setOrderNo(order.getOrderNo());
        log.setFromStatus(fromStatus);
        log.setToStatus("CANCELLED");
        log.setStatusType("ORDER");
        log.setOperatorId(SecurityContext.getUserId());
        log.setOperatorRole(SecurityContext.getUserType());
        log.setReason(reason);
        orderStatusLogMapper.insert(log);
    }

    /** 管理员手动完结订单（FULFILLING → COMPLETED） */
    @Transactional(rollbackFor = Exception.class)
    @OpLog(module = "订单", operationType = "AUDIT", targetType = "Orders")
    public void adminComplete(Long id, String reason) {
        Orders order = ordersMapper.selectById(id);
        if (order == null) throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "订单不存在");
        if (!"FULFILLING".equals(order.getOrderStatus()) && !"PICKED_UP".equals(order.getOrderStatus())
                && !"PENDING_PICKUP".equals(order.getOrderStatus())) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS, "当前状态不允许完结：" + order.getOrderStatus());
        }
        String fromStatus = order.getOrderStatus();
        order.setOrderStatus("COMPLETED");
        order.setFinishTime(LocalDateTime.now());
        ordersMapper.updateById(order);

        OrderStatusLog log = new OrderStatusLog();
        log.setOrderId(id);
        log.setOrderNo(order.getOrderNo());
        log.setFromStatus(fromStatus);
        log.setToStatus("COMPLETED");
        log.setStatusType("ORDER");
        log.setOperatorId(SecurityContext.getUserId());
        log.setOperatorRole(SecurityContext.getUserType());
        log.setReason(reason != null ? reason : "管理员手动完结");
        orderStatusLogMapper.insert(log);
    }
    /** 拆单预览（前端弹窗用：按 merchant 分组后展示子订单方案） */
    public Map<String, Object> splitPreview(Long id) {
        Orders order = ordersMapper.selectById(id);
        if (order == null) throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "订单不存在");
        validateSplittable(order);

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, id));

        // 查 merchantId 映射（via sku→product）
        Set<Long> skuIds = items.stream().map(OrderItem::getSkuId).collect(Collectors.toSet());
        Set<Long> productIds = items.stream().map(OrderItem::getProductId).collect(Collectors.toSet());
        Map<Long, Product> productMap = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));

        Map<Long, List<OrderItem>> byMerchant = new LinkedHashMap<>();
        for (OrderItem it : items) {
            Product p = productMap.get(it.getProductId());
            Long mid = p != null ? p.getMerchantId() : -1L;
            byMerchant.computeIfAbsent(mid, k -> new ArrayList<>()).add(it);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sourceOrder", order);
        List<Map<String, Object>> groups = new ArrayList<>();
        for (Map.Entry<Long, List<OrderItem>> e : byMerchant.entrySet()) {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("merchantId", e.getKey() < 0 ? null : e.getKey());
            BigDecimal subtotal = e.getValue().stream().map(OrderItem::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            g.put("subtotal", subtotal);
            g.put("items", e.getValue());
            groups.add(g);
        }
        result.put("groups", groups);
        result.put("canSplit", groups.size() >= 2);
        return result;
    }

    /** 管理员强制拆单（事务内原子操作） */
    @Transactional(rollbackFor = Exception.class)
    @OpLog(module = "订单", operationType = "AUDIT", targetType = "Orders")
    public List<Long> split(Long id, String reason) {
        Orders order = ordersMapper.selectById(id);
        if (order == null) throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "订单不存在");
        validateSplittable(order);

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, id));
        if (items.isEmpty()) throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "订单无明细可拆");

        // 查 merchantId 映射
        Set<Long> productIds = items.stream().map(OrderItem::getProductId).collect(Collectors.toSet());
        Map<Long, Product> productMap = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));

        Map<Long, List<OrderItem>> byMerchant = new LinkedHashMap<>();
        for (OrderItem it : items) {
            Product p = productMap.get(it.getProductId());
            Long mid = p != null ? p.getMerchantId() : -1L;
            byMerchant.computeIfAbsent(mid, k -> new ArrayList<>()).add(it);
        }
        if (byMerchant.size() < 2) throw new BusinessException(ErrorCode.PARAM_ERROR, "单商家订单无需拆单");

        // 1. 原订单 → SPLIT_SOURCE
        String originalNo = order.getOrderNo();
        String fromStatus = order.getOrderStatus();
        order.setOrderNo(originalNo + "_SPLIT");
        order.setOrderStatus(OrderStatusConstant.SPLIT_SOURCE);
        ordersMapper.updateById(order);

        // 原订单写审计链
        OrderStatusLog sourceLog = new OrderStatusLog();
        sourceLog.setOrderId(order.getId());
        sourceLog.setOrderNo(order.getOrderNo());
        sourceLog.setFromStatus(fromStatus);
        sourceLog.setToStatus(OrderStatusConstant.SPLIT_SOURCE);
        sourceLog.setStatusType("ORDER");
        sourceLog.setOperatorId(SecurityContext.getUserId());
        sourceLog.setOperatorRole(SecurityContext.getUserType());
        sourceLog.setReason(reason != null ? reason : "管理员拆单，拆成 " + byMerchant.size() + " 个子订单");
        orderStatusLogMapper.insert(sourceLog);

        // 2. 每商家创建子订单
        List<Long> childIds = new ArrayList<>();
        int seq = 1;
        for (Map.Entry<Long, List<OrderItem>> e : byMerchant.entrySet()) {
            Long merchantId = e.getKey() < 0 ? null : e.getKey();
            List<OrderItem> group = e.getValue();

            // 新订单
            Orders child = new Orders();
            child.setOrderNo(originalNo + "_" + seq);
            child.setUserId(order.getUserId());
            child.setMerchantId(merchantId);
            child.setCommunityId(order.getCommunityId());
            child.setPickupPointId(order.getPickupPointId());
            child.setRemark(order.getRemark());
            child.setOrderStatus(fromStatus); // 继承原订单状态（PAID/COMPLETED 等）
            child.setPayStatus(order.getPayStatus());
            child.setFulfillmentStatus(order.getFulfillmentStatus());
            child.setAftersaleStatus(order.getAftersaleStatus());
            child.setExpireTime(order.getExpireTime());
            child.setPayTime(order.getPayTime());
            child.setFinishTime(order.getFinishTime());
            child.setCancelTime(order.getCancelTime());
            child.setCancelReason(order.getCancelReason());

            BigDecimal subtotal = group.stream().map(OrderItem::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            child.setTotalAmount(subtotal);
            child.setPayAmount(subtotal);
            child.setDiscountAmount(BigDecimal.ZERO);
            child.setFreightAmount(BigDecimal.ZERO);
            child.setRefundedAmount(BigDecimal.ZERO);
            ordersMapper.insert(child);
            childIds.add(child.getId());

            // 复制 OrderItem（保持商品快照！约束§4.3 不反查 SKU）
            for (OrderItem src : group) {
                OrderItem copy = new OrderItem();
                copy.setOrderId(child.getId());
                copy.setOrderNo(child.getOrderNo());
                copy.setProductId(src.getProductId());
                copy.setSkuId(src.getSkuId());
                copy.setProductName(src.getProductName());
                copy.setSkuName(src.getSkuName());
                copy.setSpec(src.getSpec());
                copy.setImage(src.getImage());
                copy.setCategoryId(src.getCategoryId());
                copy.setPrice(src.getPrice());
                copy.setQuantity(src.getQuantity());
                copy.setSubtotal(src.getSubtotal());
                copy.setAttrSnapshot(src.getAttrSnapshot());
                copy.setRefundedQty(0);
                copy.setRefundedAmount(BigDecimal.ZERO);
                orderItemMapper.insert(copy);
            }

            // 子订单审计链
            OrderStatusLog cl = new OrderStatusLog();
            cl.setOrderId(child.getId());
            cl.setOrderNo(child.getOrderNo());
            cl.setFromStatus(OrderStatusConstant.SPLIT_SOURCE);
            cl.setToStatus(fromStatus);
            cl.setStatusType("ORDER");
            cl.setOperatorId(SecurityContext.getUserId());
            cl.setOperatorRole(SecurityContext.getUserType());
            cl.setReason("原订单 " + originalNo + " 拆单生成（商家" + (merchantId != null ? merchantId : "未绑定") + "）");
            orderStatusLogMapper.insert(cl);

            seq++;
        }

        // 3. 删除原订单的 OrderItem（已迁移到子订单）
        orderItemMapper.delete(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));

        return childIds;
    }

    /** 拆单前置校验（约束§5.2 状态机合法性） */
    private void validateSplittable(Orders order) {
        // SPLIT_SOURCE / CANCELLED / REFUNDED 绝对不能再拆
        Set<String> hardLocked = Set.of(OrderStatusConstant.CANCELLED,
                OrderStatusConstant.REFUNDING, OrderStatusConstant.REFUNDED, OrderStatusConstant.SPLIT_SOURCE);
        if (hardLocked.contains(order.getOrderStatus())) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS,
                    "当前状态 " + order.getOrderStatus() + " 不允许拆单");
        }
        // COMPLETED 等终态：仅允许 merchantId=NULL 的历史脏订单（按 merchantId 补填）
        Set<String> softLocked = Set.of(OrderStatusConstant.COMPLETED);
        if (softLocked.contains(order.getOrderStatus()) && order.getMerchantId() != null) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS,
                    "当前状态 " + order.getOrderStatus() + " 不允许拆单");
        }
    }

    /**
     * Admin 订单列表首图：实时取 product.mainImage（与订单详情/商品管理图片统一）
     */
    private Map<Long, String> loadFirstItemImageMap(List<Orders> orders) {
        Set<Long> orderIds = orders.stream().map(Orders::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (orderIds.isEmpty()) return Map.of();

        List<OrderItem> allItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, orderIds));
        if (allItems.isEmpty()) return Map.of();

        // 每订单取第一个 item
        Map<Long, String> snapshotMap = new LinkedHashMap<>();
        Map<Long, List<OrderItem>> grouped = allItems.stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));
        for (Map.Entry<Long, List<OrderItem>> e : grouped.entrySet()) {
            OrderItem first = e.getValue().get(0);
            snapshotMap.put(e.getKey(), first.getImage());
        }

        // 批量 join 实时 product.mainImage 覆盖
        Set<Long> pids = allItems.stream().map(OrderItem::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (pids.isEmpty()) return snapshotMap;
        Map<Long, Product> pMap = productMapper.selectBatchIds(pids).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
        for (Map.Entry<Long, String> e : snapshotMap.entrySet()) {
            OrderItem firstItem = grouped.get(e.getKey()).get(0);
            Product p = pMap.get(firstItem.getProductId());
            if (p != null && p.getMainImage() != null && !p.getMainImage().isBlank()) {
                e.setValue(p.getMainImage());
            }
        }
        return snapshotMap;
    }

    /** Admin 订单详情图片统一（同 OrderService.overrideImagesWithLiveProduct）*/
    private void overrideImagesWithLiveProduct(List<OrderItem> items) {
        if (items == null || items.isEmpty()) return;
        Set<Long> pids = items.stream().map(OrderItem::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (pids.isEmpty()) return;
        Map<Long, Product> pMap = productMapper.selectBatchIds(pids).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
        for (OrderItem it : items) {
            Product p = pMap.get(it.getProductId());
            if (p != null && p.getMainImage() != null && !p.getMainImage().isBlank()) {
                it.setImage(p.getMainImage());
            }
        }
    }}