package com.project.groupbuy.modules.recommend.controller;

import com.project.groupbuy.common.result.R;
import com.project.groupbuy.common.context.SecurityContext;
import com.project.groupbuy.modules.recommend.service.RecommendService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 个性化推荐控制器 —— 约束 L753-L763
 * - 严格限制在 consumer 模块使用（商家/管理端看不到个性化）
 * - personalized 参数可关闭，降级为综合排序
 */
@RestController
@RequestMapping("/recommend")
@RequiredArgsConstructor
public class RecommendController {

    private final RecommendService recommendService;

    /** 首页推荐：4 个区块一次返回 */
    @GetMapping("/home")
    public R<Map<String, Object>> home(
            @RequestParam(required = false) Long communityId,
            @RequestParam(defaultValue = "true") boolean personalized) {
        Long userId = SecurityContext.getUserId(); // 可能为 null（未登录）
        return R.ok(recommendService.home(communityId, userId, personalized));
    }

    /** 上报浏览事件（前端商品详情页调用，异步写入） */
    @PostMapping("/browse")
    public R<Void> browse(@RequestBody Map<String, Object> body) {
        Long userId = SecurityContext.getUserId();
        Long productId    = body.get("productId") != null    ? Long.valueOf(body.get("productId").toString())    : null;
        Long categoryId   = body.get("categoryId") != null   ? Long.valueOf(body.get("categoryId").toString())   : null;
        Long communityId  = body.get("communityId") != null  ? Long.valueOf(body.get("communityId").toString())  : null;
        int  durationMs   = body.get("durationMs") != null   ? Integer.parseInt(body.get("durationMs").toString()) : 0;
        recommendService.recordBrowse(userId, productId, categoryId, communityId, durationMs);
        return R.ok();
    }

    /** 社区爆款排行榜 */
    @GetMapping("/hot")
    public R<Object> hot(@RequestParam Long communityId,
                         @RequestParam(defaultValue = "20") int limit) {
        return R.ok(recommendService.hotByCommunity(communityId, limit));
    }

    /** 即将到货 */
    @GetMapping("/coming-soon")
    public R<Object> comingSoon(@RequestParam(required = false) Long communityId,
                                @RequestParam(defaultValue = "10") int limit) {
        return R.ok(recommendService.comingSoon(communityId, limit));
    }

    /** 上报加购事件（异步更新分类偏好权重 +3） */
    @PostMapping("/cart")
    public R<Void> cart(@RequestParam(required = false) Long productId,
                        @RequestParam(required = false) Long categoryId,
                        @RequestParam(required = false) Long communityId) {
        Long userId = SecurityContext.getUserId();
        // 如果调用方只传了 productId，也接受，Service 内部不依赖
        recommendService.recordCart(userId, categoryId);
        return R.ok();
    }

    /** 上报下单事件（异步更新分类偏好权重 +5） */
    @PostMapping("/buy")
    public R<Void> buy(@RequestParam(required = false) Long productId,
                       @RequestParam(required = false) Long categoryId,
                       @RequestParam(required = false) Long communityId) {
        Long userId = SecurityContext.getUserId();
        recommendService.recordBuy(userId, categoryId);
        return R.ok();
    }
}
