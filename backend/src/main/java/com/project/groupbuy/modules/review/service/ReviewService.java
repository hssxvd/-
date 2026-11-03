package com.project.groupbuy.modules.review.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.project.groupbuy.common.context.SecurityContext;
import com.project.groupbuy.common.exception.BusinessException;
import com.project.groupbuy.common.result.ErrorCode;
import com.project.groupbuy.modules.order.entity.OrderItem;
import com.project.groupbuy.modules.order.entity.Orders;
import com.project.groupbuy.modules.order.mapper.OrderItemMapper;
import com.project.groupbuy.modules.order.mapper.OrdersMapper;
import com.project.groupbuy.modules.product.entity.Product;
import com.project.groupbuy.modules.product.entity.ProductSku;
import com.project.groupbuy.modules.product.mapper.ProductMapper;
import com.project.groupbuy.modules.product.mapper.ProductSkuMapper;
import com.project.groupbuy.modules.review.entity.Review;
import com.project.groupbuy.modules.review.mapper.ReviewMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.project.groupbuy.common.constant.OrderStatusConstant;

/**
 * 评价服务（课题"售后评价"）
 *
 * 约束：
 * - 同一订单明细只能评一次
 * - 只有 COMPLETED 订单可评
 * - 评分 1-5 星
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewMapper reviewMapper;
    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ProductMapper productMapper;

    /**
     * 发表评价
     */
    @Transactional(rollbackFor = Exception.class)
    public Review create(Long orderItemId, Integer rating, String content, String images, Integer isAnonymous) {
        Long userId = SecurityContext.getUserId();
        OrderItem oi = orderItemMapper.selectById(orderItemId);
        if (oi == null) throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "订单明细不存在");

        // 校验订单归属
        Orders order = ordersMapper.selectById(oi.getOrderId());
        if (!order.getUserId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);

        // 只有 COMPLETED 可评
        if (!OrderStatusConstant.COMPLETED.equals(order.getOrderStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "只有已完成订单可评价");
        }
        // 约束 5.2：终态（REFUNDING / REFUNDED / CANCELLED）禁止评价
        if (OrderStatusConstant.isTerminal(order.getOrderStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该订单已进入终态，禁止评价");
        }

        // 同一明细只能评一次
        Long existCount = reviewMapper.selectCount(
                new LambdaQueryWrapper<Review>().eq(Review::getOrderItemId, orderItemId));
        if (existCount > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该订单明细已评价");
        }

        // 评分 1-5
        if (rating == null || rating < 1 || rating > 5) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "评分必须在 1-5 之间");
        }

        Review r = new Review();
        r.setOrderId(order.getId());
        r.setOrderItemId(orderItemId);
        r.setUserId(userId);
        r.setSkuId(oi.getSkuId());
        r.setMerchantId(order.getMerchantId());
        r.setRating(rating);
        r.setContent(content);
        r.setImages(images);
        r.setIsAnonymous(isAnonymous != null ? isAnonymous : 0);
        reviewMapper.insert(r);
        return r;
    }

    /**
     * 商家回复评价（首次回复）
     */
    @Transactional(rollbackFor = Exception.class)
    public Review reply(Long reviewId, String replyContent) {
        Long merchantId = SecurityContext.getMerchantId();
        Review r = reviewMapper.selectById(reviewId);
        if (r == null) throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "评价不存在");
        if (!merchantId.equals(r.getMerchantId())) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (r.getReplyContent() != null && !r.getReplyContent().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该评价已回复，请使用编辑功能修改");
        }
        r.setReplyContent(replyContent);
        r.setReplyTime(LocalDateTime.now());
        reviewMapper.updateById(r);
        return r;
    }

    /**
     * 商家编辑已提交的回复（约束文档：商家可修改自己的回复）
     */
    @Transactional(rollbackFor = Exception.class)
    public Review editReply(Long reviewId, String replyContent) {
        Long merchantId = SecurityContext.getMerchantId();
        Review r = reviewMapper.selectById(reviewId);
        if (r == null) throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "评价不存在");
        if (!merchantId.equals(r.getMerchantId())) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (r.getReplyContent() == null || r.getReplyContent().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "尚未回复，请先回复");
        }
        r.setReplyContent(replyContent);
        r.setReplyTime(LocalDateTime.now());
        reviewMapper.updateById(r);
        return r;
    }

    /**
     * 商品评价列表
     */
    public List<Review> listBySku(Long skuId, Integer rating) {
        LambdaQueryWrapper<Review> w = new LambdaQueryWrapper<>();
        w.eq(Review::getSkuId, skuId);
        w.eq(rating != null, Review::getRating, rating);
        w.orderByDesc(Review::getCreateTime);
        List<Review> list = reviewMapper.selectList(w);
        fillSkuInfo(list);
        return list;
    }

    /**
     * 我的评价列表
     */
    public List<Review> listMine() {
        Long userId = SecurityContext.getUserId();
        List<Review> list = reviewMapper.selectList(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getUserId, userId)
                        .orderByDesc(Review::getCreateTime));
        fillSkuInfo(list);
        return list;
    }

    // ===== 批量填充 transient 商品名/规格名（前端展示用，避免 N+1）=====

    private void fillSkuInfo(List<Review> reviews) {
        if (reviews == null || reviews.isEmpty()) return;
        // 1. 收集所有 skuIds → 批量查 ProductSku
        Set<Long> skuIds = reviews.stream().map(Review::getSkuId).collect(Collectors.toSet());
        if (skuIds.isEmpty()) return;
        Map<Long, ProductSku> skuMap = productSkuMapper.selectBatchIds(skuIds)
                .stream().collect(Collectors.toMap(ProductSku::getId, s -> s));
        // 2. 收集所有 productIds → 批量查 Product
        Set<Long> productIds = skuMap.values().stream().map(ProductSku::getProductId).collect(Collectors.toSet());
        Map<Long, Product> productMap = productIds.isEmpty() ? Map.of() :
                productMapper.selectBatchIds(productIds).stream()
                        .collect(Collectors.toMap(Product::getId, p -> p));
        // 3. 回填 transient 字段
        for (Review r : reviews) {
            ProductSku sku = skuMap.get(r.getSkuId());
            if (sku != null) {
                r.setSkuName(sku.getSkuName());
                Product p = productMap.get(sku.getProductId());
                if (p != null) {
                    r.setProductName(p.getProductName());
                    r.setProductMainImage(p.getMainImage());
                }
            }
        }
    }

    /**
     * 商家评价列表（数据隔离）
     */
    public List<Review> listByMerchant(Integer rating) {
        Long merchantId = SecurityContext.getMerchantId();
        LambdaQueryWrapper<Review> w = new LambdaQueryWrapper<>();
        w.eq(Review::getMerchantId, merchantId);
        w.eq(rating != null, Review::getRating, rating);
        w.orderByDesc(Review::getCreateTime);
        List<Review> list = reviewMapper.selectList(w);
        fillSkuInfo(list);
        return list;
    }

    /**
     * 星级占比
     */
    public Map<Integer, Long> ratingDistribution(Long skuId) {
        List<Review> reviews = reviewMapper.selectList(
                new LambdaQueryWrapper<Review>().eq(Review::getSkuId, skuId));
        return reviews.stream().collect(Collectors.groupingBy(Review::getRating, Collectors.counting()));
    }

    /**
     * 平均评分
     */
    public double avgRating(Long skuId) {
        List<Review> reviews = reviewMapper.selectList(
                new LambdaQueryWrapper<Review>().eq(Review::getSkuId, skuId));
        if (reviews.isEmpty()) return 0;
        return reviews.stream().mapToInt(Review::getRating).average().orElse(0);
    }

    // ===== Product 粒度聚合（前端"全部规格评价"tab 用）=====

    /** 同一 product 下所有 SKU */
    private List<Long> productSkuIds(Long productId) {
        return productSkuMapper.selectList(
                new LambdaQueryWrapper<ProductSku>()
                        .eq(ProductSku::getProductId, productId)
                        .eq(ProductSku::getIsDeleted, 0))
                .stream().map(ProductSku::getId).toList();
    }

    /** 商品全规格评价列表 */
    public List<Review> listByProduct(Long productId, Integer rating) {
        List<Long> skuIds = productSkuIds(productId);
        if (skuIds.isEmpty()) return List.of();
        LambdaQueryWrapper<Review> w = new LambdaQueryWrapper<>();
        w.in(Review::getSkuId, skuIds);
        w.eq(rating != null, Review::getRating, rating);
        w.orderByDesc(Review::getCreateTime);
        List<Review> list = reviewMapper.selectList(w);
        fillSkuInfo(list);
        return list;
    }

    /** 商品全规格星级占比 */
    public Map<Integer, Long> ratingDistributionByProduct(Long productId) {
        List<Long> skuIds = productSkuIds(productId);
        if (skuIds.isEmpty()) return Map.of();
        List<Review> reviews = reviewMapper.selectList(
                new LambdaQueryWrapper<Review>().in(Review::getSkuId, skuIds));
        return reviews.stream().collect(Collectors.groupingBy(Review::getRating, Collectors.counting()));
    }

    /** 商品全规格平均分 */
    public double avgRatingByProduct(Long productId) {
        List<Long> skuIds = productSkuIds(productId);
        if (skuIds.isEmpty()) return 0;
        List<Review> reviews = reviewMapper.selectList(
                new LambdaQueryWrapper<Review>().in(Review::getSkuId, skuIds));
        if (reviews.isEmpty()) return 0;
        return reviews.stream().mapToInt(Review::getRating).average().orElse(0);
    }
}
