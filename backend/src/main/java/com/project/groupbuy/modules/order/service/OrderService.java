package com.project.groupbuy.modules.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.project.groupbuy.common.context.SecurityContext;
import com.project.groupbuy.common.exception.BusinessException;
import com.project.groupbuy.common.result.ErrorCode;
import com.project.groupbuy.modules.order.entity.OrderItem;
import com.project.groupbuy.modules.order.entity.OrderStatusLog;
import com.project.groupbuy.modules.order.entity.Orders;
import com.project.groupbuy.modules.order.mapper.OrderItemMapper;
import com.project.groupbuy.modules.order.mapper.OrderStatusLogMapper;
import com.project.groupbuy.modules.order.mapper.OrdersMapper;
import com.project.groupbuy.modules.product.entity.ProductAttribute;
import com.project.groupbuy.modules.product.entity.ProductSku;
import com.project.groupbuy.modules.product.mapper.ProductAttributeMapper;
import com.project.groupbuy.modules.product.mapper.ProductSkuMapper;
import com.project.groupbuy.modules.supply.entity.Stock;
import com.project.groupbuy.modules.review.entity.Review;
import com.project.groupbuy.modules.review.mapper.ReviewMapper;
import com.project.groupbuy.modules.supply.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.hutool.core.util.IdUtil;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import com.project.groupbuy.common.constant.OrderStatusConstant;
import com.project.groupbuy.common.constant.UserTypeConstant;

/**
 * 订单服务（约束文档 4.3 订单快照 + 5.2 订单状态机）
 *
 * 订单创建：商品快照 + 特色属性快照 + 库存预占
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStatusLogMapper orderStatusLogMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ProductAttributeMapper productAttributeMapper;
    private final ReviewMapper reviewMapper;
    private final StockService stockService;
    private final com.project.groupbuy.modules.product.mapper.ProductMapper productMapper;
    private final com.project.groupbuy.modules.cart.service.CartService cartService;

    /**
     * 创建订单（业务闭环起点）
     *
     * 【约束文档 §8 数据隔离】本方法按 SKU→Product→merchant_id 分组后**拆成多笔订单**：
     * 一个购物车里选了 A 商家和 B 商家的商品，会生成 2 笔独立 Orders 记录，
     * 保证 orders.merchant_id 与其中所有 OrderItem 的归属商家严格一致。
     *
     * 事务原子性：校验（全部 SKU 一次性校验，失败则回滚）→ 拆单落库 → 库存预占 → 清空购物车
     *
     * @return 生成的订单列表（至少 1 笔，多商家购物车返回多笔）
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Orders> create(Long communityId, Long pickupPointId, List<OrderItemCreateItem> items, String remark) {
        Long userId = SecurityContext.getUserId();

        // 0. 如果 items 为空，从购物车取选中项（业务闭环：购物车→订单）
        if (items == null || items.isEmpty()) {
            List<com.project.groupbuy.modules.cart.entity.CartItem> selected = cartService.listSelected();
            if (selected.isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "购物车为空，请先添加商品");
            }
            items = selected.stream().map(ci -> {
                OrderItemCreateItem item = new OrderItemCreateItem();
                item.setSkuId(ci.getSkuId());
                item.setQuantity(ci.getQuantity());
                return item;
            }).toList();
        }

        // 1. ✅ N+1 修复（v1.19）：批量预查所有 stock 一次性校验
        List<StockInfo> allStockInfos = new ArrayList<>();
        Set<Long> skuIds = items.stream().map(OrderItemCreateItem::getSkuId).collect(Collectors.toSet());
        Map<Long, Stock> stockMap = stockService.batchGetBySkusAndCommunity(skuIds, communityId, pickupPointId);
        Map<Long, ProductSku> skuMap = productSkuMapper.selectBatchIds(skuIds).stream()
                .collect(Collectors.toMap(ProductSku::getId, s -> s));
        for (OrderItemCreateItem item : items) {
            ProductSku sku = skuMap.get(item.getSkuId());
            if (sku == null || !"ACTIVE".equals(sku.getStatus())) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "SKU 不存在或已下架");
            }
            Stock stock = stockMap.get(item.getSkuId());
            if (stock == null || stock.getAvailableQty() < item.getQuantity()) {
                throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH,
                        String.format("SKU %d 库存不足，可用 %d，需要 %d",
                                item.getSkuId(),
                                stock == null ? 0 : stock.getAvailableQty(),
                                item.getQuantity()));
            }
            allStockInfos.add(new StockInfo(sku, stock, item.getQuantity()));
        }

        // 1.5 批量预查 product Map（约束 4.3 订单快照 image + attr 兜底，避免 N+1；同时用于按 merchantId 分组）
        Set<Long> productIds = allStockInfos.stream().map(si -> si.sku.getProductId()).collect(Collectors.toSet());
        Map<Long, com.project.groupbuy.modules.product.entity.Product> productMap =
                productMapper.selectBatchIds(productIds).stream()
                        .collect(Collectors.toMap(com.project.groupbuy.modules.product.entity.Product::getId, p -> p));

        // ✅ N+1 修复：特色属性快照批量预查，避免 for 循环里 selectList
        Map<Long, List<ProductAttribute>> highlightAttrsByProduct = Collections.emptyMap();
        if (!productIds.isEmpty()) {
            highlightAttrsByProduct = productAttributeMapper.selectList(
                    new LambdaQueryWrapper<ProductAttribute>()
                            .in(ProductAttribute::getProductId, productIds)
                            .eq(ProductAttribute::getIsHighlight, 1))
                    .stream().collect(Collectors.groupingBy(ProductAttribute::getProductId));
        }

        // 【核心改动】按 merchantId 分组 —— 每一组生成 1 笔独立订单
        Map<Long, List<StockInfo>> byMerchant = new LinkedHashMap<>();
        for (StockInfo si : allStockInfos) {
            com.project.groupbuy.modules.product.entity.Product p = productMap.get(si.sku.getProductId());
            Long mid = (p != null) ? p.getMerchantId() : null;
            byMerchant.computeIfAbsent(mid, k -> new ArrayList<>()).add(si);
        }

        List<Orders> result = new ArrayList<>();

        for (Map.Entry<Long, List<StockInfo>> entry : byMerchant.entrySet()) {
            Long merchantId = entry.getKey();
            List<StockInfo> groupStockInfos = entry.getValue();

            // 2. 每商家独立创建 Orders
            // ✅ 订单号修复（v1.19）：雪花 ID 替换 System.currentTimeMillis() + 局部 orderSeq
            //   旧逻辑: baseTs = System.currentTimeMillis()，同一毫秒内两次独立调用 → baseTs 相同
            //          + 局部 orderSeq 各自从 0 开始 → ORD{ts}{""} 冲突
            //   新逻辑: IdUtil.getSnowflakeNextIdStr() 全局唯一，单 JVM 自增
            String orderNo = "ORD" + IdUtil.getSnowflakeNextIdStr();
            Orders order = new Orders();   // 经验 100023216：每组独立 new，不复用
            order.setOrderNo(orderNo);
            order.setUserId(userId);
            order.setMerchantId(merchantId);
            order.setCommunityId(communityId);
            order.setPickupPointId(pickupPointId);
            order.setRemark(remark);
            order.setOrderStatus(OrderStatusConstant.PENDING_PAY);
            order.setPayStatus("PENDING");
            order.setExpireTime(LocalDateTime.now().plusMinutes(15));

            BigDecimal total = BigDecimal.ZERO;
            for (StockInfo si : groupStockInfos) {
                total = total.add(si.sku.getPrice().multiply(BigDecimal.valueOf(si.qty)));
            }
            order.setTotalAmount(total);
            order.setPayAmount(total);
            // 约束 28.17: NOT NULL DEFAULT 0 字段必须显式 SET（MyBatis-Plus 会写 NULL 覆盖 MySQL DEFAULT）
            order.setDiscountAmount(BigDecimal.ZERO);
            order.setFreightAmount(BigDecimal.ZERO);
            order.setRefundedAmount(BigDecimal.ZERO);
            ordersMapper.insert(order);

            // 3. 每商家独立创建 OrderItem（约束文档 4.3：商品快照 + 特色属性快照）
            for (StockInfo si : groupStockInfos) {
                OrderItem oi = new OrderItem();   // 经验 100023216：每条明细独立 new
                oi.setOrderId(order.getId());
                oi.setOrderNo(orderNo);
                oi.setProductId(si.sku.getProductId());
                oi.setSkuId(si.sku.getId());
                oi.setProductName(si.sku.getSkuName());
                oi.setSkuName(si.sku.getSkuName());
                oi.setSpec(si.sku.getSpec());
                // 约束 4.3 订单快照：sku.image 通常为 NULL（SKU 共用 product.main_image），用 productMap 兜底
                String skuImg = si.sku.getImage();
                String mainImg = productMap.get(si.sku.getProductId()) != null
                        ? productMap.get(si.sku.getProductId()).getMainImage() : null;
                oi.setImage((skuImg != null && !skuImg.isBlank()) ? skuImg : mainImg);
                oi.setPrice(si.sku.getPrice());
                oi.setQuantity(si.qty);
                oi.setSubtotal(si.sku.getPrice().multiply(BigDecimal.valueOf(si.qty)));

                // 特色属性快照（约束文档 4.3 + 4.5：JSON 格式）
                // ✅ N+1 修复：从预查好的 Map 取，不再 for 循环里 selectList
                List<ProductAttribute> attrs = highlightAttrsByProduct.getOrDefault(si.sku.getProductId(), Collections.emptyList());
                if (!attrs.isEmpty()) {
                    String attrSnapshot = attrs.stream()
                            .map(a -> String.format("{\"key\":\"%s\",\"value\":\"%s\"}",
                                    a.getAttrKey(), a.getAttrValue()))
                            .collect(Collectors.joining(",", "[", "]"));
                    oi.setAttrSnapshot(attrSnapshot);
                }

                orderItemMapper.insert(oi);
            }

            // 4. 库存预占（约束文档 6：条件更新防超卖）
            for (StockInfo si : groupStockInfos) {
                stockService.lock(si.stock.getId(), si.qty, orderNo);
            }

            // 5. 每商家独立写状态日志
            writeLog(order.getId(), orderNo, null, OrderStatusConstant.PENDING_PAY, "ORDER", userId, "用户下单（拆单-商家" + merchantId + "）");

            result.add(order);
        }

        // 6. 清空购物车选中项（所有拆单成功后才清，保证原子性）
        cartService.clearSelected();

        log.info("约束§8 拆单完成：{} 个商家 → {} 笔订单，userId={}", byMerchant.size(), result.size(), userId);
        return result;
    }

    /**
     * 取消订单（待支付状态）
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long orderId, String reason) {
        Long userId = SecurityContext.getUserId();
        Orders order = ordersMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (!OrderStatusConstant.PENDING_PAY.equals(order.getOrderStatus())) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS, "订单状态不允许取消");
        }

        // 1. 更新订单状态
        String oldStatus = order.getOrderStatus();
        order.setOrderStatus(OrderStatusConstant.CANCELLED);
        order.setCancelReason(reason);
        order.setCancelTime(LocalDateTime.now());
        ordersMapper.updateById(order);

        // 2. 释放库存
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        if (!items.isEmpty()) {
            // ✅ N+1 修复（v1.19）：批量查 stock
            Set<Long> skuIds = items.stream().map(OrderItem::getSkuId).collect(Collectors.toSet());
            Map<Long, Stock> stockMap = stockService.batchGetBySkusAndCommunity(
                    skuIds, order.getCommunityId(), order.getPickupPointId());
            for (OrderItem oi : items) {
                Stock stock = stockMap.get(oi.getSkuId());
                if (stock != null) {
                    stockService.unlock(stock.getId(), oi.getQuantity(), order.getOrderNo(), reason);
                }
            }
        }

        // 3. 写日志
        writeLog(orderId, order.getOrderNo(), oldStatus, OrderStatusConstant.CANCELLED, "ORDER", userId, reason);
    }

    /**
     * 按业务编号查订单详情（约束 7.2.3：前端用 orderNo 查单，后端必须提供对应入口）
     */
    public Map<String, Object> detailByOrderNo(String orderNo) {
        Orders order = ordersMapper.selectOne(
                new LambdaQueryWrapper<Orders>().eq(Orders::getOrderNo, orderNo));
        if (order == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "订单不存在：" + orderNo);
        }
        return detail(order.getId());
    }

    /**
     * 订单详情（约束文档 4.3：特色属性快照展示）
     */
    public Map<String, Object> detail(Long orderId) {
        Long userId = SecurityContext.getUserId();
        Orders order = ordersMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND);
        }
        // 约束 8 数据隔离：消费者看自己的 / 管理员看全部 / 商家看自己 merchantId 的
        // 【关键修复】商家身份除了看 order.merchant_id，还要查是否有属于该商家的 item
        // —— 兼容 V14 把脏订单 merchant_id 置 NULL 的情况
        String userType = SecurityContext.getUserType();
        Long merchantId = SecurityContext.getMerchantId();
        boolean canView = order.getUserId().equals(userId)
                || UserTypeConstant.isAdmin(userType);
        if (!canView && UserTypeConstant.MERCHANT.equals(userType)) {
            // 先看 order.merchant_id 是否匹配（正常拆单后的订单都走这条路）
            if (order.getMerchantId() != null && order.getMerchantId().equals(merchantId)) {
                canView = true;
            } else {
                // 历史脏订单（merchant_id 为 NULL 或不匹配）：查 item 归属
                Long matchCount = orderItemMapper.selectCount(
                        new LambdaQueryWrapper<OrderItem>()
                                .eq(OrderItem::getOrderId, orderId)
                                .inSql(OrderItem::getSkuId,
                                        "SELECT sk.id FROM product_sku sk" +
                                        " JOIN product p ON p.id = sk.product_id" +
                                        " WHERE p.is_deleted = 0 AND sk.is_deleted = 0 AND p.merchant_id = " + merchantId));
                canView = matchCount != null && matchCount > 0;
            }
        }
        if (!canView) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("order", order);

        // 【B 方案兜底】商家端查看详情时，按 item 归属过滤 —— 即使 orders.merchant_id 匹配，
        // 存量脏订单里可能混有别家的 items，这里也过滤掉
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        if (UserTypeConstant.MERCHANT.equals(userType)) {
            Set<Long> skuIds = items.stream().map(OrderItem::getSkuId).collect(Collectors.toSet());
            if (!skuIds.isEmpty()) {
                Map<Long, Long> skuToMerchantId = new HashMap<>();
                List<com.project.groupbuy.modules.product.entity.ProductSku> skus =
                        productSkuMapper.selectBatchIds(skuIds);
                Set<Long> pids = skus.stream().map(com.project.groupbuy.modules.product.entity.ProductSku::getProductId).collect(Collectors.toSet());
                Map<Long, Long> p2m = productMapper.selectBatchIds(pids).stream()
                        .collect(Collectors.toMap(com.project.groupbuy.modules.product.entity.Product::getId,
                                com.project.groupbuy.modules.product.entity.Product::getMerchantId));
                for (com.project.groupbuy.modules.product.entity.ProductSku sku : skus) {
                    skuToMerchantId.put(sku.getId(), p2m.get(sku.getProductId()));
                }
                items = items.stream()
                        .filter(oi -> { Long mid = skuToMerchantId.get(oi.getSkuId()); return mid == null || merchantId.equals(mid); })
                        .collect(Collectors.toList());
            }
        }
        overrideImagesWithLiveProduct(items);
        result.put("items", items);

        result.put("logs", orderStatusLogMapper.selectList(
                new LambdaQueryWrapper<OrderStatusLog>().eq(OrderStatusLog::getOrderNo, order.getOrderNo())
                        .orderByAsc(OrderStatusLog::getCreateTime)));
        return result;
    }

    /**
     * 订单列表（消费者）
     */
    /**
     * 我的订单（消费者），每条聚合 OrderItem 快照（约束文档 4.3：订单明细必须做商品快照）
     */
    public List<Map<String, Object>> listMine(Long userId) {
        List<Orders> orders = ordersMapper.selectList(
                new LambdaQueryWrapper<Orders>()
                        .eq(Orders::getUserId, userId)
                        .orderByDesc(Orders::getCreateTime));
        if (orders.isEmpty()) return Collections.emptyList();

        // 批量查 OrderItem，按 orderId 分组
        Set<Long> orderIds = orders.stream().map(Orders::getId).collect(Collectors.toSet());
        List<OrderItem> allItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, orderIds));
        Map<Long, List<OrderItem>> itemsMap = allItems.stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));

        // 批量查已评价 orderItemId（避免 N+1，约束 5.2 终态守卫在 ReviewService 内部）
        Set<Long> allItemIds = allItems.stream().map(OrderItem::getId).collect(Collectors.toSet());
        Set<Long> reviewedItemIds = allItemIds.isEmpty() ? Collections.emptySet() :
                reviewMapper.selectList(
                        new LambdaQueryWrapper<Review>()
                                .in(Review::getOrderItemId, allItemIds)
                                .eq(Review::getIsDeleted, 0))
                        .stream().map(Review::getOrderItemId).collect(Collectors.toSet());
        itemsMap.values().forEach(list -> list.forEach(it -> it.setReviewed(reviewedItemIds.contains(it.getId()))));

        return orders.stream().map(o -> {
            Map<String, Object> m = new LinkedHashMap<>();
            // 订单级字段全部平铺（前端 Orders.vue 直接访问 o.totalAmount / o.orderStatus）
            m.put("id", o.getId());
            m.put("orderNo", o.getOrderNo());
            m.put("userId", o.getUserId());
            m.put("merchantId", o.getMerchantId());
            m.put("communityId", o.getCommunityId());
            m.put("pickupPointId", o.getPickupPointId());
            m.put("totalAmount", o.getTotalAmount());
            m.put("discountAmount", o.getDiscountAmount());
            m.put("freightAmount", o.getFreightAmount());
            m.put("payAmount", o.getPayAmount());
            m.put("refundedAmount", o.getRefundedAmount());
            m.put("orderStatus", o.getOrderStatus());
            // 约束 5.5 v1.14：订单维度售后进度聚合，前端「售后」Tab 与订单卡片售后标签依赖该字段
            m.put("aftersaleStatus", o.getAftersaleStatus());
            m.put("payStatus", o.getPayStatus());
            m.put("fulfillmentStatus", o.getFulfillmentStatus());
            m.put("pickupCode", o.getPickupCode());
            m.put("payTime", o.getPayTime());
            m.put("expireTime", o.getExpireTime());
            m.put("createTime", o.getCreateTime());
            m.put("remark", o.getRemark());
            m.put("source", o.getSource());
            // 商品明细快照（约束文档 4.3）
// 订单明细：展示层用实时 product.mainImage（让订单管理与商品管理图片统一），
            // 但 order_item.image 快照仍保留在 DB（约束§4.3 物证）
            List<OrderItem> ois = new ArrayList<>(itemsMap.getOrDefault(o.getId(), Collections.emptyList()));
            overrideImagesWithLiveProduct(ois);
            m.put("items", ois);
            return m;
        }).collect(Collectors.toList());
    }

    /**
     * 商家订单列表（约束文档 8：数据隔离，强制 merchant_id）
     * 聚合 OrderItem 快照 + 商品摘要 —— 和 listMine 同一模式
     *
     * 【约束 §8 重构】orders 集合也按 item 归属过滤（而非 orders.merchant_id），
     * 原因：后端 create() 已按 merchantId 拆单（orders.merchant_id 应该永远和 items 归属一致），
     * 但 V14 把历史脏订单 merchant_id 置为 NULL（无法准确归属），
     * 必须用 item 归属查询才能让商家看到自己的商品交易
     */
    public List<Map<String, Object>> listByMerchant(String status) {
        Long merchantId = SecurityContext.getMerchantId();
        LambdaQueryWrapper<Orders> w = new LambdaQueryWrapper<>();
        // 【关键修复】用 item 归属子查询代替 orders.merchant_id 过滤
        w.inSql(Orders::getId,
                "SELECT DISTINCT oi.order_id FROM order_item oi" +
                " JOIN product_sku sk ON sk.id = oi.sku_id" +
                " JOIN product p ON p.id = sk.product_id" +
                " WHERE oi.is_deleted = 0 AND sk.is_deleted = 0 AND p.is_deleted = 0" +
                " AND p.merchant_id = " + merchantId);
        w.eq(status != null, Orders::getOrderStatus, status);
        w.orderByDesc(Orders::getCreateTime);
        List<Orders> orders = ordersMapper.selectList(w);
        if (orders.isEmpty()) return Collections.emptyList();

        Set<Long> orderIds = orders.stream().map(Orders::getId).collect(Collectors.toSet());
        List<OrderItem> allItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, orderIds));

        // 【B 方案兜底】过滤掉不属于当前商家的 items：join product_sku → product，按 merchant_id 过滤
        // 先批量查 sku → product_id → product.merchant_id
        Set<Long> skuIds = allItems.stream().map(OrderItem::getSkuId).collect(Collectors.toSet());
        Map<Long, Long> skuToMerchantId = new HashMap<>();   // skuId → merchantId
        if (!skuIds.isEmpty()) {
            List<com.project.groupbuy.modules.product.entity.ProductSku> skus =
                    productSkuMapper.selectBatchIds(skuIds);
            Set<Long> productIds = skus.stream().map(com.project.groupbuy.modules.product.entity.ProductSku::getProductId).collect(Collectors.toSet());
            Map<Long, Long> productToMerchant = productMapper.selectBatchIds(productIds).stream()
                    .collect(Collectors.toMap(com.project.groupbuy.modules.product.entity.Product::getId,
                            com.project.groupbuy.modules.product.entity.Product::getMerchantId));
            for (com.project.groupbuy.modules.product.entity.ProductSku sku : skus) {
                skuToMerchantId.put(sku.getId(), productToMerchant.get(sku.getProductId()));
            }
        }

        // 分组时按 item 归属过滤
        Map<Long, List<OrderItem>> itemsMap = allItems.stream()
                .filter(oi -> {
                    Long itemMid = skuToMerchantId.get(oi.getSkuId());
                    // 只保留归属 merchantId 匹配的（或 product 查不到时兜底：保留避免误伤）
                    return itemMid == null || merchantId.equals(itemMid);
                })
                .collect(Collectors.groupingBy(OrderItem::getOrderId));

        // 过滤掉所有 items 都不属于自己的订单（跨商家脏订单的全部商品都过滤掉）
        return orders.stream()
                .filter(o -> !itemsMap.getOrDefault(o.getId(), Collections.emptyList()).isEmpty())
                .map(o -> {
                    List<OrderItem> items = itemsMap.get(o.getId());
                    // 商品摘要：最多 3 个 "苹果脆片 500g ×2"，多的加 "等 N 件"
                    String summary = items.stream()
                            .limit(3)
                            .map(i -> i.getProductName() + (i.getSkuName() != null ? "(" + i.getSkuName() + ")" : "") + "×" + i.getQuantity())
                            .collect(Collectors.joining("、"));
                    if (items.size() > 3) summary += " 等" + items.size() + "件";

                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("order", o);
                    overrideImagesWithLiveProduct(items);
                    row.put("items", items);
                    row.put("itemCount", items.size());
                    row.put("summary", summary);
                    return row;
                }).collect(Collectors.toList());
    }

    private void writeLog(Long orderId, String orderNo, String from, String to, String type, Long userId, String reason) {
        OrderStatusLog log = new OrderStatusLog();
        log.setOrderId(orderId);
        log.setOrderNo(orderNo);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setStatusType(type);
        log.setOperatorId(userId);
        log.setReason(reason);
        orderStatusLogMapper.insert(log);
    }

    // ===== 内部类 =====

    public static class OrderItemCreateItem {
        private Long skuId;
        private Integer quantity;

        public Long getSkuId() { return skuId; }
        public void setSkuId(Long skuId) { this.skuId = skuId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }

    private static class StockInfo {
        ProductSku sku;
        Stock stock;
        Integer qty;

        StockInfo(ProductSku sku, Stock stock, Integer qty) {
            this.sku = sku;
            this.stock = stock;
            this.qty = qty;
        }
    }

    /**
     * 订单管理展示层图片统一：把 OrderItem 快照 image 用实时 Product.mainImage 覆盖。
     * 约束§4.3 DB 层 order_item.image 仍保留原始快照（物证用途），此处仅覆盖返回给前端的展示值。
     */
    private void overrideImagesWithLiveProduct(List<OrderItem> items) {
        if (items == null || items.isEmpty()) return;
        Set<Long> pids = items.stream().map(OrderItem::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (pids.isEmpty()) return;
        Map<Long, com.project.groupbuy.modules.product.entity.Product> pMap =
                productMapper.selectBatchIds(pids).stream()
                        .collect(Collectors.toMap(
                                com.project.groupbuy.modules.product.entity.Product::getId,
                                p -> p, (a, b) -> a));
        for (OrderItem it : items) {
            com.project.groupbuy.modules.product.entity.Product p = pMap.get(it.getProductId());
            if (p != null && p.getMainImage() != null && !p.getMainImage().isBlank()) {
                it.setImage(p.getMainImage());
            }
        }
    }}