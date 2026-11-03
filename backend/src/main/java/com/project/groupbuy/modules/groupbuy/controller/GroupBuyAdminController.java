package com.project.groupbuy.modules.groupbuy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.project.groupbuy.common.context.SecurityContext;
import com.project.groupbuy.common.exception.BusinessException;
import com.project.groupbuy.common.result.ErrorCode;
import com.project.groupbuy.common.result.R;
import com.project.groupbuy.modules.groupbuy.entity.GroupBuyActivity;
import com.project.groupbuy.modules.groupbuy.entity.GroupBuyMember;
import com.project.groupbuy.modules.groupbuy.mapper.GroupBuyActivityMapper;
import com.project.groupbuy.modules.groupbuy.mapper.GroupBuyMemberMapper;
import com.project.groupbuy.modules.groupbuy.service.GroupBuyService;
import com.project.groupbuy.modules.order.entity.Orders;
import com.project.groupbuy.modules.order.mapper.OrdersMapper;
import com.project.groupbuy.modules.product.entity.Merchant;
import com.project.groupbuy.modules.product.entity.Product;
import com.project.groupbuy.modules.product.entity.ProductSku;
import com.project.groupbuy.modules.product.mapper.MerchantMapper;
import com.project.groupbuy.modules.product.mapper.ProductMapper;
import com.project.groupbuy.modules.product.mapper.ProductSkuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商家端团购管理
 * - 商家可以创建/查看自己的团购活动
 */
@RestController
@RequestMapping("/merchant/group-buys")
@RequiredArgsConstructor
public class GroupBuyAdminController {

    private final GroupBuyService groupBuyService;
    private final GroupBuyActivityMapper activityMapper;
    private final GroupBuyMemberMapper memberMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;
    private final MerchantMapper merchantMapper;
    private final OrdersMapper ordersMapper;

    /** 商家自己的团购活动列表 */
    @GetMapping
    public R<List<GroupBuyActivity>> listMine() {
        Long merchantId = SecurityContext.getUserId();
        LambdaQueryWrapper<GroupBuyActivity> q = new LambdaQueryWrapper<GroupBuyActivity>()
                .eq(GroupBuyActivity::getMerchantId, merchantId)
                .orderByDesc(GroupBuyActivity::getCreateTime);
        List<GroupBuyActivity> list = activityMapper.selectList(q);
        groupBuyService.fillTransientsInternal(list);
        return R.ok(list);
    }

    /**
     * 商家创建新团购活动
     * body: { productId, skuId, title, groupPrice, minGroupQty, maxGroupQty,
     *        cycleDays(默认3), communityId(可选), autoCutoff(默认1) }
     */
    @PostMapping
    public R<GroupBuyActivity> create(@RequestBody Map<String, Object> body) {
        Long merchantId = SecurityContext.getUserId();

        Long productId = Long.valueOf(body.get("productId").toString());
        Long skuId = Long.valueOf(body.get("skuId").toString());
        String title = (String) body.get("title");
        BigDecimal groupPrice = new BigDecimal(body.get("groupPrice").toString());
        Integer minGroupQty = (Integer) body.get("minGroupQty");
        Integer maxGroupQty = (Integer) body.get("maxGroupQty");
        Integer minGroupMembers = body.containsKey("minGroupMembers")
                ? (Integer) body.get("minGroupMembers") : 2;
        Integer maxQtyPerPerson = body.containsKey("maxQtyPerPerson")
                ? (Integer) body.get("maxQtyPerPerson") : null;
        Integer cycleDays = body.containsKey("cycleDays") ? (Integer) body.get("cycleDays") : 3;
        Long communityId = body.get("communityId") != null ? Long.valueOf(body.get("communityId").toString()) : null;

        // 未指定社区时，从商家绑定的社区取
        if (communityId == null) {
            Merchant m = merchantMapper.selectById(merchantId);
            if (m != null && m.getCommunityId() != null) {
                communityId = m.getCommunityId();
            } else {
                communityId = 1L; // 兜底
            }
        }

        // 1. 校验商品 + SKU 归属当前商家
        Product product = productMapper.selectById(productId);
        if (product == null) throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "商品不存在");
        if (!product.getMerchantId().equals(merchantId))
            throw new BusinessException(ErrorCode.PARAM_ERROR, "无权操作非本店商品");

        ProductSku sku = productSkuMapper.selectById(skuId);
        if (sku == null) throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "SKU 不存在");
        if (!sku.getProductId().equals(productId))
            throw new BusinessException(ErrorCode.PARAM_ERROR, "SKU 不属于该商品");

        // 2. 校验团价低于原价
        BigDecimal originalPrice = sku.getPrice() != null ? sku.getPrice() : product.getPrice();
        if (originalPrice != null && groupPrice.compareTo(originalPrice) >= 0)
            throw new BusinessException(ErrorCode.PARAM_ERROR, "团购价必须低于原价");

        // 3. 校验数量（预售模式：窗口到期就结算，门槛可以放宽）
        if (minGroupQty == null || minGroupQty < 1)
            throw new BusinessException(ErrorCode.PARAM_ERROR, "目标销量不能小于1");
        if (maxGroupQty == null || maxGroupQty < minGroupQty)
            throw new BusinessException(ErrorCode.PARAM_ERROR, "最大可售量必须大于目标销量");
        if (maxQtyPerPerson != null && maxQtyPerPerson < 1)
            throw new BusinessException(ErrorCode.PARAM_ERROR, "每人限购件数至少为1");

        // 4. 组装 activity
        GroupBuyActivity a = new GroupBuyActivity();
        a.setActivityNo("GB-M-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + merchantId);
        a.setTitle(title != null ? title : (product.getProductName() + " 团购"));
        a.setProductId(productId);
        a.setSkuId(skuId);
        a.setMerchantId(merchantId);
        a.setCommunityId(communityId);
        a.setMinGroupQty(minGroupQty);
        a.setMaxGroupQty(maxGroupQty);
        a.setMinGroupMembers(minGroupMembers == null ? 2 : minGroupMembers);
        a.setMaxQtyPerPerson(maxQtyPerPerson);
        a.setGroupPrice(groupPrice);
        a.setOriginalPrice(originalPrice);
        a.setDiscountPercent(originalPrice != null
                ? originalPrice.subtract(groupPrice).divide(originalPrice, 4, BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal("100"))
                : null);
        a.setCycleStart(LocalDateTime.now());
        a.setCycleEnd(LocalDateTime.now().plusDays(cycleDays));
        a.setExpectedArrive(a.getCycleEnd().plusDays(1));
        a.setStatus("OPEN");
        a.setAutoCutoff(1);
        a.setCreateBy(merchantId);
        activityMapper.insert(a);

        groupBuyService.fillTransientsInternal(java.util.Collections.singletonList(a));
        return R.ok(a);
    }

    /** 商家关闭自己的预售活动（仅 OPEN 状态可关闭） */
    @PostMapping("/{id}/cancel")
    public R<Map<String, Object>> cancel(@PathVariable Long id) {
        Long merchantId = SecurityContext.getUserId();
        GroupBuyActivity a = activityMapper.selectById(id);
        if (a == null) throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "活动不存在");
        if (!a.getMerchantId().equals(merchantId))
            throw new BusinessException(ErrorCode.PARAM_ERROR, "无权操作");
        if (!"OPEN".equals(a.getStatus()))
            throw new BusinessException(ErrorCode.PARAM_ERROR, "活动已结束，无法关闭");

        a.setStatus("CANCELLED");
        activityMapper.updateById(a);
        return R.ok(Map.of("msg", "活动已关闭"));
    }

    /** 商家一键配货：CLOSED 活动 → 成员订单 fulfillment_status = PENDING_PICKUP
     *  预售模式：窗口结束自动生成了 orders（PENDING_PAY），商家确认配货后改为等待自提 */
    @PostMapping("/{id}/ready")
    public R<Map<String, Object>> markReady(@PathVariable Long id) {
        Long merchantId = SecurityContext.getUserId();
        GroupBuyActivity a = activityMapper.selectById(id);
        if (a == null) throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "活动不存在");
        if (!a.getMerchantId().equals(merchantId))
            throw new BusinessException(ErrorCode.PARAM_ERROR, "无权操作");
        if (!"CLOSED".equals(a.getStatus()))
            throw new BusinessException(ErrorCode.PARAM_ERROR, "活动状态不允许配货（需先截止下单）");

        // 把该活动所有 member 关联的订单 fulfillment_status 改为 PENDING_PICKUP
        List<GroupBuyMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<GroupBuyMember>()
                        .eq(GroupBuyMember::getActivityId, id)
                        .isNotNull(GroupBuyMember::getOrderId));
        int updated = 0;
        for (GroupBuyMember m : members) {
            Orders o = ordersMapper.selectById(m.getOrderId());
            if (o != null && !"COMPLETED".equals(o.getFulfillmentStatus())) {
                o.setFulfillmentStatus("PENDING_PICKUP");
                ordersMapper.updateById(o);
                updated++;
            }
        }
        return R.ok(Map.of("msg", "已通知 " + updated + " 位用户取货", "updatedOrders", updated));
    }

    /** 商家查看某个团购的成员 + 订单状态 */
    @GetMapping("/{id}/members")
    public R<List<GroupBuyMember>> members(@PathVariable Long id) {
        Long merchantId = SecurityContext.getUserId();
        GroupBuyActivity a = activityMapper.selectById(id);
        if (a == null) throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "活动不存在");
        if (!a.getMerchantId().equals(merchantId))
            throw new BusinessException(ErrorCode.PARAM_ERROR, "无权查看");

        List<GroupBuyMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<GroupBuyMember>()
                        .eq(GroupBuyMember::getActivityId, id)
                        .orderByDesc(GroupBuyMember::getIsLeader));
        return R.ok(members);
    }
}

