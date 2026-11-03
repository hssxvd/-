package com.project.groupbuy.modules.review.controller;

import com.project.groupbuy.common.result.R;
import com.project.groupbuy.modules.review.entity.Review;
import com.project.groupbuy.modules.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 评价控制器（课题"售后评价"）
 */
@Slf4j
@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * 发表评价
     */
    @PostMapping
    public R<Review> create(@RequestParam Long orderItemId,
                             @RequestParam Integer rating,
                             @RequestParam(required = false) String content,
                             @RequestParam(required = false) String images,
                             @RequestParam(required = false, defaultValue = "0") Integer isAnonymous) {
        return R.ok(reviewService.create(orderItemId, rating, content, images, isAnonymous));
    }

    /**
     * 商家回复评价（首次回复）
     */
    @PostMapping("/{id}/reply")
    public R<Review> reply(@PathVariable Long id, @RequestParam String replyContent) {
        return R.ok(reviewService.reply(id, replyContent));
    }

    /**
     * 商家编辑已提交的回复
     */
    @PutMapping("/{id}/reply")
    public R<Review> editReply(@PathVariable Long id, @RequestParam String replyContent) {
        return R.ok(reviewService.editReply(id, replyContent));
    }

    /**
     * 商品评价列表
     */
    @GetMapping("/sku/{skuId}")
    public R<List<Review>> listBySku(@PathVariable Long skuId, @RequestParam(required = false) Integer rating) {
        return R.ok(reviewService.listBySku(skuId, rating));
    }

    /**
     * 我的评价
     */
    @GetMapping("/mine")
    public R<List<Review>> mine() {
        return R.ok(reviewService.listMine());
    }

    /**
     * 商家评价列表
     */
    @GetMapping("/merchant")
    public R<List<Review>> merchant(@RequestParam(required = false) Integer rating) {
        return R.ok(reviewService.listByMerchant(rating));
    }

    /**
     * 星级占比
     */
    @GetMapping("/distribution/{skuId}")
    public R<Map<Integer, Long>> distribution(@PathVariable Long skuId) {
        return R.ok(reviewService.ratingDistribution(skuId));
    }

    /**
     * 平均评分
     */
    @GetMapping("/avg/{skuId}")
    public R<Double> avg(@PathVariable Long skuId) {
        return R.ok(reviewService.avgRating(skuId));
    }

    // ===== Product 粒度聚合（公开，商品详情页"全部规格评价"tab）=====

    /** 商品全规格评价列表 */
    @GetMapping("/product/{productId}")
    public R<List<Review>> listByProduct(@PathVariable Long productId, @RequestParam(required = false) Integer rating) {
        return R.ok(reviewService.listByProduct(productId, rating));
    }

    /** 商品全规格平均分 */
    @GetMapping("/avg-product/{productId}")
    public R<Double> avgByProduct(@PathVariable Long productId) {
        return R.ok(reviewService.avgRatingByProduct(productId));
    }

    /** 商品全规格星级占比 */
    @GetMapping("/distribution-product/{productId}")
    public R<Map<Integer, Long>> distributionByProduct(@PathVariable Long productId) {
        return R.ok(reviewService.ratingDistributionByProduct(productId));
    }
}
