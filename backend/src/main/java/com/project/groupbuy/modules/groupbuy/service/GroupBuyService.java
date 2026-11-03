package com.project.groupbuy.modules.groupbuy.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.project.groupbuy.common.exception.BusinessException;
import com.project.groupbuy.common.result.ErrorCode;
import com.project.groupbuy.modules.groupbuy.entity.GroupBuyActivity;
import com.project.groupbuy.modules.groupbuy.entity.GroupBuyMember;
import com.project.groupbuy.modules.groupbuy.entity.GroupBuyProgress;
import com.project.groupbuy.modules.groupbuy.mapper.GroupBuyActivityMapper;
import com.project.groupbuy.modules.groupbuy.mapper.GroupBuyMemberMapper;
import com.project.groupbuy.modules.groupbuy.mapper.GroupBuyProgressMapper;
import com.project.groupbuy.modules.order.entity.OrderItem;
import com.project.groupbuy.modules.order.entity.Orders;
import com.project.groupbuy.modules.order.mapper.OrderItemMapper;
import com.project.groupbuy.modules.order.mapper.OrdersMapper;
import com.project.groupbuy.modules.product.entity.Product;
import com.project.groupbuy.modules.product.entity.ProductSku;
import com.project.groupbuy.modules.product.mapper.ProductMapper;
import com.project.groupbuy.modules.product.mapper.ProductSkuMapper;
import com.project.groupbuy.modules.supply.entity.Stock;
import com.project.groupbuy.modules.supply.mapper.StockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 社区团购服务 —— 项目名"社区团购"核心功能
 * 核心：SKU x Community x Cycle = 一个团实例
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupBuyService {

    private final GroupBuyActivityMapper activityMapper;
    private final GroupBuyMemberMapper memberMapper;
    private final GroupBuyProgressMapper progressMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;
    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;
    private final StockMapper stockMapper;

    /** 列表接口：返回 OPEN 状态 + 批量填充进度和商品信息 */
    public List<GroupBuyActivity> listOpen(Long communityId) {
        LambdaQueryWrapper<GroupBuyActivity> q = new LambdaQueryWrapper<GroupBuyActivity>()
                .eq(GroupBuyActivity::getStatus, "OPEN")
                .lt(GroupBuyActivity::getCycleStart, LocalDateTime.now())
                .gt(GroupBuyActivity::getCycleEnd, LocalDateTime.now())
                .orderByAsc(GroupBuyActivity::getCycleEnd);
        if (communityId != null) q.eq(GroupBuyActivity::getCommunityId, communityId);
        List<GroupBuyActivity> list = activityMapper.selectList(q);
        fillTransientsInternal(list);
        return list;
    }

    /** 详情接口：同样填充进度和商品信息 */
    public GroupBuyActivity detail(Long id) {
        GroupBuyActivity a = activityMapper.selectById(id);
        if (a == null) throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "团购活动不存在");
        fillTransientsInternal(Collections.singletonList(a));
        return a;
    }

    /** 批量填充虚拟字段（public 供 Controller 直接调用） */
    public void fillTransientsInternal(List<GroupBuyActivity> activities) {
        if (activities == null || activities.isEmpty()) return;

        // 1. 批量聚合 member 表：SUM(quantity) GROUP BY activity_id
        List<Long> activityIds = activities.stream().map(GroupBuyActivity::getId).collect(Collectors.toList());
        LambdaQueryWrapper<GroupBuyMember> mw = new LambdaQueryWrapper<GroupBuyMember>()
                .in(GroupBuyMember::getActivityId, activityIds);
        List<GroupBuyMember> allMembers = memberMapper.selectList(mw);
        Map<Long, List<GroupBuyMember>> membersByActivity = allMembers.stream()
                .collect(Collectors.groupingBy(GroupBuyMember::getActivityId));

        // 2. 批量查 product
        Set<Long> productIds = activities.stream()
                .map(GroupBuyActivity::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Product> productMap = productIds.isEmpty()
                ? Collections.emptyMap()
                : productMapper.selectBatchIds(productIds).stream()
                        .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));

        // 3. 填充
        for (GroupBuyActivity a : activities) {
            List<GroupBuyMember> ms = membersByActivity.getOrDefault(a.getId(), Collections.emptyList());
            int joinedQty = ms.stream().mapToInt(GroupBuyMember::getQuantity).sum();
            int minMembers = a.getMinGroupMembers() == null ? 2 : a.getMinGroupMembers();
            a.setJoinedQty(joinedQty);
            a.setJoinedMembers(ms.size());
            a.setIsFormed((joinedQty >= a.getMinGroupQty() && ms.size() >= minMembers) ? 1 : 0);

            Product p = productMap.get(a.getProductId());
            if (p != null) {
                a.setProductName(p.getProductName());
                a.setProductImage(p.getMainImage());
            }
        }
    }

    /** 预售下单（写 member 记录，窗口结束后统一生成订单） */
    @Transactional(rollbackFor = Exception.class)
    public GroupBuyMember join(Long activityId, Long userId, int quantity, Long pickupPointId) {
        GroupBuyActivity a = detail(activityId);
        // 预售模式：只有 OPEN 状态 + 窗口未关闭 可以下单
        if (!"OPEN".equals(a.getStatus()))
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS, "活动已结束，无法下单");
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(a.getCycleStart()))
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS, "活动尚未开始");
        if (now.isAfter(a.getCycleEnd()))
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS, "下单窗口已关闭");
        if (quantity <= 0 || quantity > 10)
            throw new BusinessException(ErrorCode.PARAM_ERROR, "购买数量必须在 1-10 之间");

        // 库存校验：按 sku + community 查询可用库存
        Long skuId = a.getSkuId();
        Long communityId = a.getCommunityId();
        if (skuId != null) {
            Stock stk = stockMapper.selectOne(
                    new LambdaQueryWrapper<Stock>()
                            .eq(Stock::getSkuId, skuId)
                            .eq(Stock::getCommunityId, communityId)
                            .last("LIMIT 1"));
            int available = stk == null ? 0 : stk.getAvailableQty();
            if (quantity > available)
                throw new BusinessException(ErrorCode.PARAM_ERROR, "库存不足，仅剩 " + available + " 件");
        }

        // 最大可售量校验
        int totalOrdered = memberMapper.selectList(
                new LambdaQueryWrapper<GroupBuyMember>().eq(GroupBuyMember::getActivityId, activityId))
                .stream().mapToInt(GroupBuyMember::getQuantity).sum();
        if (totalOrdered + quantity > a.getMaxGroupQty())
            throw new BusinessException(ErrorCode.PARAM_ERROR, "已超过活动最大可售量");

        // 查是否已下单（同一活动重复下单 → 累加数量）
        GroupBuyMember existing = memberMapper.selectOne(
                new LambdaQueryWrapper<GroupBuyMember>()
                        .eq(GroupBuyMember::getActivityId, activityId)
                        .eq(GroupBuyMember::getUserId, userId));

        int maxPerPerson = a.getMaxQtyPerPerson() == null
                ? Math.min(10, a.getMaxGroupQty())
                : a.getMaxQtyPerPerson();

        if (existing != null) {
            int newQty = existing.getQuantity() + quantity;
            if (newQty > maxPerPerson)
                throw new BusinessException(ErrorCode.PARAM_ERROR, "超出单人限购，累计最多 " + maxPerPerson + " 件");
            existing.setQuantity(newQty);
            if (pickupPointId != null) existing.setPickupPointId(pickupPointId);
            memberMapper.updateById(existing);
            // 预售模式：不再 refreshProgress 触发成团 — 直接返回
            return existing;
        }

        if (quantity > maxPerPerson)
            throw new BusinessException(ErrorCode.PARAM_ERROR, "超出单人限购，最多 " + maxPerPerson + " 件");

        GroupBuyMember m = new GroupBuyMember();
        m.setActivityId(activityId);
        m.setUserId(userId);
        m.setQuantity(quantity);
        m.setPickupPointId(pickupPointId != null ? pickupPointId : a.getPickupPointId());
        m.setGroupPrice(a.getGroupPrice());
        m.setIsLeader(0);
        m.setPayStatus("UNPAID");
        memberMapper.insert(m);

        log.info("[预售] 用户 {} 下单活动 {} qty={} pickupPoint={}", userId, activityId, quantity, m.getPickupPointId());
        return m;
    }

    /** 预售模式：刷新活动进度（冗余表） */
    public GroupBuyProgress refreshProgress(Long activityId) {
        GroupBuyActivity a = activityMapper.selectById(activityId);
        if (a == null) return null;

        List<GroupBuyMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<GroupBuyMember>().eq(GroupBuyMember::getActivityId, activityId));
        int joinedQty = members.stream().mapToInt(GroupBuyMember::getQuantity).sum();
        int joinedCount = members.size();
        // 预售模式：进度 = 已预订 / 最大可售（不再用 minGroupQty 算进度）
        int target = a.getMaxGroupQty() != null && a.getMaxGroupQty() > 0
                ? a.getMaxGroupQty() : a.getMinGroupQty();
        int pct = Math.min(100, (int) Math.round(joinedQty * 100.0 / target));
        // 预售模式：isFormed = 窗口是否已关闭（cycle_end 到期）
        boolean windowClosed = LocalDateTime.now().isAfter(a.getCycleEnd());

        GroupBuyProgress p = progressMapper.selectById(activityId);
        if (p == null) {
            p = new GroupBuyProgress();
            p.setActivityId(activityId);
            p.setJoinedQty(joinedQty);
            p.setJoinedCount(joinedCount);
            p.setProgressPct(pct);
            p.setIsFormed(windowClosed ? 1 : 0);
            progressMapper.insert(p);
        } else {
            p.setJoinedQty(joinedQty);
            p.setJoinedCount(joinedCount);
            p.setProgressPct(pct);
            p.setIsFormed(windowClosed ? 1 : 0);
            progressMapper.updateById(p);
        }
        return p;
    }

    /** 查活动实时进度 */
    public GroupBuyProgress getProgress(Long activityId) {
        refreshProgress(activityId);
        return progressMapper.selectById(activityId);
    }

    /**
     * 预售模式定时任务：每 10 分钟扫描 cycle_end 过期的 OPEN 活动
     *   - 窗口关闭 → 状态改 CLOSED → 给每个 member 生成订单（永远生成，不够量也不取消）
     *   - 没人下单的活动直接改 CLOSED，不生成订单
     */
    @Scheduled(cron = "0 */10 * * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void settleWindowEnd() {
        List<GroupBuyActivity> expired = activityMapper.selectList(
                new LambdaQueryWrapper<GroupBuyActivity>()
                        .eq(GroupBuyActivity::getStatus, "OPEN")
                        .lt(GroupBuyActivity::getCycleEnd, LocalDateTime.now()));

        for (GroupBuyActivity a : expired) {
            GroupBuyProgress p = refreshProgress(a.getId());
            if (p.getJoinedCount() > 0) {
                log.info("[预售] 活动 {} 窗口关闭，共 {} 用户下单 {} 份，开始生成订单",
                        a.getId(), p.getJoinedCount(), p.getJoinedQty());
                a.setStatus("CLOSED");
                generateGroupBuyOrders(a);
            } else {
                log.info("[预售] 活动 {} 窗口关闭但无人下单，直接关闭", a.getId());
                a.setStatus("CANCELLED"); // 无人下单 → 取消，商家零损失
            }
            activityMapper.updateById(a);
        }
    }

    /**
     * 成团后自动给每个 member 生成一条 order + order_item（幂等）
     * 约束：成团即锁定参团意愿，用户在 24h 内完成支付即可
     */
    @Transactional(rollbackFor = Exception.class)
    public void generateGroupBuyOrders(GroupBuyActivity a) {
        List<GroupBuyMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<GroupBuyMember>()
                        .eq(GroupBuyMember::getActivityId, a.getId())
                        .isNull(GroupBuyMember::getOrderId)); // 幂等：只处理还没生成订单的

        if (members.isEmpty()) {
            log.info("[团购] 活动 {} 所有 member 已生成过订单，跳过", a.getId());
            return;
        }

        // 查商品 & SKU 快照（订单生成后商品名不能变，快照固化）
        Product product = a.getProductId() != null ? productMapper.selectById(a.getProductId()) : null;
        ProductSku sku = a.getSkuId() != null ? productSkuMapper.selectById(a.getSkuId()) : null;

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        int successCount = 0;

        for (GroupBuyMember m : members) {
            try {
                // 1. 生成 Orders
                Orders order = new Orders();
                String orderNo = "GB" + LocalDateTime.now().format(fmt) + String.format("%04d", IdUtil.getSnowflakeNextId() % 10000);
                order.setOrderNo(orderNo);
                order.setUserId(m.getUserId());
                order.setMerchantId(a.getMerchantId());
                order.setCommunityId(a.getCommunityId());
                // 预售模式：优先 member 自己选的自提点，其次活动指定，都没有则 null
                Long pickupPid = m.getPickupPointId() != null ? m.getPickupPointId() : a.getPickupPointId();
                order.setPickupPointId(pickupPid);

                BigDecimal totalAmount = a.getGroupPrice().multiply(BigDecimal.valueOf(m.getQuantity()));
                order.setTotalAmount(totalAmount);
                order.setPayAmount(totalAmount); // 团购已经是团价，无额外优惠
                order.setDiscountAmount(BigDecimal.ZERO);
                order.setFreightAmount(BigDecimal.ZERO);

                order.setOrderStatus("PENDING_PAY");
                order.setPayStatus("PENDING");
                order.setFulfillmentStatus("PENDING_ASSIGN");
                order.setAftersaleStatus("NONE");
                order.setSource("GROUP_BUY");
                order.setRemark("团购活动 #" + a.getId() + " 自动生成");
                order.setExpireTime(LocalDateTime.now().plusHours(24)); // 24h 支付窗口

                ordersMapper.insert(order);

                // 2. 生成 OrderItem（快照）
                OrderItem item = new OrderItem();
                item.setOrderId(order.getId());
                item.setOrderNo(orderNo);
                item.setProductId(a.getProductId());
                item.setSkuId(a.getSkuId());
                item.setProductName(product != null ? product.getProductName() : a.getTitle());
                item.setSkuName(sku != null ? sku.getSkuName() : null);
                item.setSpec(sku != null ? sku.getSpec() : null);
                item.setImage(product != null ? product.getMainImage() : null);
                item.setCategoryId(product != null ? product.getCategoryId() : null);
                item.setPrice(a.getGroupPrice());
                item.setQuantity(m.getQuantity());
                item.setSubtotal(totalAmount);
                orderItemMapper.insert(item);

                // 3. 回填 member.order_id + 改 pay_status
                m.setOrderId(order.getId());
                m.setPayStatus("PENDING");
                memberMapper.updateById(m);

                successCount++;
                log.info("[团购订单] activity={} member={} user={} orderNo={} qty={} amount={}",
                        a.getId(), m.getId(), m.getUserId(), orderNo, m.getQuantity(), totalAmount);
            } catch (Exception e) {
                log.error("[团购订单] 活动 {} member={} 生成订单失败: {}", a.getId(), m.getId(), e.getMessage(), e);
            }
        }
        log.info("[团购] 活动 {} 自动生成订单完成: {}/{} 成功", a.getId(), successCount, members.size());
    }
}
