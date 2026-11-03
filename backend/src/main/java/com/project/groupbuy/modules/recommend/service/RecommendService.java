package com.project.groupbuy.modules.recommend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.project.groupbuy.modules.product.entity.Product;
import com.project.groupbuy.modules.product.mapper.ProductMapper;
import com.project.groupbuy.modules.recommend.entity.UserBrowseHistory;
import com.project.groupbuy.modules.recommend.entity.UserCategoryPreference;
import com.project.groupbuy.modules.recommend.mapper.UserBrowseHistoryMapper;
import com.project.groupbuy.modules.recommend.mapper.UserCategoryPreferenceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 个性化推荐服务 —— 严格遵守约束 L759-L763
 * 仅使用：社区爆款 / 即将到货 / 浏览分类偏好 / 加购下单分类偏好（白名单内）
 * 禁止：具体地址 / 真实姓名 / 手机号 / 身份证号 / 支付信息
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendService {

    private final UserBrowseHistoryMapper browseMapper;
    private final UserCategoryPreferenceMapper prefMapper;
    private final ProductMapper productMapper;

    /** 首页个性化推荐（4 个区块） */
    public Map<String, Object> home(Long communityId, Long userId, boolean personalized) {
        Map<String, Object> data = new HashMap<>();
        data.put("communityHot", hotByCommunity(communityId, 10));
        data.put("comingSoon",   comingSoon(communityId, 5));
        data.put("recentViewed", recentViewed(userId, 8));

        if (personalized && userId != null) {
            data.put("categoryPreferred", preferredByUser(userId, communityId, 10));
        } else {
            data.put("categoryPreferred", hotByCommunity(communityId, 10)); // 降级：综合排序
        }
        return data;
    }

    /** 社区爆款（近 30 天综合 + 新品排序） */
    public List<Product> hotByCommunity(Long communityId, int limit) {
        if (communityId == null) return new ArrayList<>();
        return productMapper.selectList(
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getCommunityId, communityId)
                        .eq(Product::getStatus, "ON_SHELF")
                        .orderByDesc(Product::getCreateTime)
                        .last("LIMIT " + limit));
    }

    /** 即将到货（有 deadline_time 在未来 3 天内的商品） */
    public List<Product> comingSoon(Long communityId, int limit) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime future = now.plusDays(3);
        LambdaQueryWrapper<Product> q = new LambdaQueryWrapper<>();
        q.eq(Product::getStatus, "ON_SHELF")
         .isNotNull(Product::getDeadlineTime)
         .ge(Product::getDeadlineTime, now)
         .le(Product::getDeadlineTime, future);
        if (communityId != null) q.eq(Product::getCommunityId, communityId);
        return productMapper.selectList(q.last("LIMIT " + limit));
    }

    /** 用户分类偏好推荐（relevance_score 加权） */
    public List<Product> preferredByUser(Long userId, Long communityId, int limit) {
        if (userId == null) return new ArrayList<>();
        List<UserCategoryPreference> prefs = prefMapper.selectList(
                new LambdaQueryWrapper<UserCategoryPreference>()
                        .eq(UserCategoryPreference::getUserId, userId)
                        .orderByDesc(UserCategoryPreference::getTotalWeight)
                        .last("LIMIT 3"));
        if (prefs.isEmpty()) return hotByCommunity(communityId, limit);
        List<Long> cats = prefs.stream().map(UserCategoryPreference::getCategoryId).toList();
        LambdaQueryWrapper<Product> q = new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, "ON_SHELF")
                .in(Product::getCategoryId, cats)
                .orderByDesc(Product::getCreateTime)
                .last("LIMIT " + limit);
        if (communityId != null) q.eq(Product::getCommunityId, communityId);
        return productMapper.selectList(q);
    }

    /** 最近浏览 */
    public List<Product> recentViewed(Long userId, int limit) {
        if (userId == null) return new ArrayList<>();
        List<UserBrowseHistory> history = browseMapper.selectList(
                new LambdaQueryWrapper<UserBrowseHistory>()
                        .eq(UserBrowseHistory::getUserId, userId)
                        .orderByDesc(UserBrowseHistory::getCreateTime)
                        .last("LIMIT " + (limit * 4)));
        if (history.isEmpty()) return new ArrayList<>();
        List<Long> pids = history.stream().map(UserBrowseHistory::getProductId).distinct().limit(limit).toList();
        return productMapper.selectBatchIds(pids);
    }

    /** 异步写入浏览历史 + 更新分类偏好快照 */
    @Async
    public void recordBrowse(Long userId, Long productId, Long categoryId, Long communityId, int durationMs) {
        if (userId == null || productId == null) return;
        UserBrowseHistory h = new UserBrowseHistory();
        h.setUserId(userId); h.setProductId(productId);
        h.setCategoryId(categoryId); h.setCommunityId(communityId);
        h.setDurationMs(durationMs);
        browseMapper.insert(h);
        updatePreference(userId, categoryId, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Async
    public void recordCart(Long userId, Long categoryId) {
        if (userId == null) return;
        updatePreference(userId, categoryId, BigDecimal.ZERO, BigDecimal.valueOf(3), BigDecimal.ZERO);
    }

    @Async
    public void recordBuy(Long userId, Long categoryId) {
        if (userId == null) return;
        updatePreference(userId, categoryId, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(5));
    }

    private void updatePreference(Long userId, Long categoryId,
                                  BigDecimal browseDelta, BigDecimal cartDelta, BigDecimal buyDelta) {
        if (categoryId == null) return;
        UserCategoryPreference p = prefMapper.selectOne(
                new LambdaQueryWrapper<UserCategoryPreference>()
                        .eq(UserCategoryPreference::getUserId, userId)
                        .eq(UserCategoryPreference::getCategoryId, categoryId));
        if (p == null) {
            p = new UserCategoryPreference();
            p.setUserId(userId); p.setCategoryId(categoryId);
            p.setBrowseWeight(browseDelta);
            p.setCartWeight(cartDelta);
            p.setBuyWeight(buyDelta);
            p.setTotalWeight(browseDelta.add(cartDelta).add(buyDelta));
            p.setLastVisitAt(LocalDateTime.now());
            prefMapper.insert(p);
        } else {
            p.setBrowseWeight(p.getBrowseWeight().add(browseDelta));
            p.setCartWeight(p.getCartWeight().add(cartDelta));
            p.setBuyWeight(p.getBuyWeight().add(buyDelta));
            p.setTotalWeight(p.getBrowseWeight().add(p.getCartWeight()).add(p.getBuyWeight()));
            p.setLastVisitAt(LocalDateTime.now());
            prefMapper.updateById(p);
        }
    }
}
