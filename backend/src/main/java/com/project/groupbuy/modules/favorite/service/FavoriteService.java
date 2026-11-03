package com.project.groupbuy.modules.favorite.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.project.groupbuy.common.context.SecurityContext;
import com.project.groupbuy.common.exception.BusinessException;
import com.project.groupbuy.common.result.ErrorCode;
import com.project.groupbuy.modules.favorite.entity.ProductFavorite;
import com.project.groupbuy.modules.favorite.mapper.ProductFavoriteMapper;
import com.project.groupbuy.modules.product.entity.Merchant;
import com.project.groupbuy.modules.product.entity.Product;
import com.project.groupbuy.modules.product.mapper.MerchantMapper;
import com.project.groupbuy.modules.product.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 商品收藏服务
 * 收藏比购物车简单：只有 user + product 关联，不涉及 SKU/数量/库存
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final ProductFavoriteMapper favoriteMapper;
    private final ProductMapper productMapper;
    private final MerchantMapper merchantMapper;

    /**
     * 收藏 / 取消收藏（toggle 语义，前端不用先查状态再决定调用哪个接口）
     * @return true=已收藏  false=已取消
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean toggle(Long productId) {
        Long userId = SecurityContext.getUserId();

        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "商品不存在");
        }

        ProductFavorite exist = favoriteMapper.selectOne(
                new LambdaQueryWrapper<ProductFavorite>()
                        .eq(ProductFavorite::getUserId, userId)
                        .eq(ProductFavorite::getProductId, productId)
                        .last("LIMIT 1"));

        if (exist != null) {
            // 已存在 → 取消（逻辑删除恢复）
            if (exist.getIsDeleted() != null && exist.getIsDeleted() == 0) {
                favoriteMapper.deleteById(exist.getId()); // 逻辑删除
                return false;
            } else {
                // 已逻辑删除的 → 恢复
                exist.setIsDeleted(0);
                favoriteMapper.updateById(exist);
                return true;
            }
        } else {
            // 新增
            ProductFavorite fav = new ProductFavorite();
            fav.setUserId(userId);
            fav.setProductId(productId);
            favoriteMapper.insert(fav);
            return true;
        }
    }

    /** 单独查：当前用户是否已收藏 */
    public boolean isFavorited(Long productId) {
        Long userId = SecurityContext.getUserId();
        if (userId == null) return false;
        return favoriteMapper.selectCount(
                new LambdaQueryWrapper<ProductFavorite>()
                        .eq(ProductFavorite::getUserId, userId)
                        .eq(ProductFavorite::getProductId, productId)) > 0;
    }

    /** 批量查：一组 productId 的收藏状态（商品列表页用） */
    public Set<Long> favoritedProductIds(Collection<Long> productIds) {
        Long userId = SecurityContext.getUserId();
        if (userId == null || productIds == null || productIds.isEmpty()) {
            return Collections.emptySet();
        }
        return favoriteMapper.selectList(
                new LambdaQueryWrapper<ProductFavorite>()
                        .eq(ProductFavorite::getUserId, userId)
                        .in(ProductFavorite::getProductId, productIds))
                .stream().map(ProductFavorite::getProductId).collect(Collectors.toSet());
    }

    /**
     * 我的收藏列表（含商品详情 + 商家名）
     */
    public Page<Map<String, Object>> listMine(int pageNum, int pageSize) {
        Long userId = SecurityContext.getUserId();

        Page<ProductFavorite> favPage = new Page<>(Math.max(1, pageNum), Math.min(50, pageSize));
        favoriteMapper.selectPage(favPage,
                new LambdaQueryWrapper<ProductFavorite>()
                        .eq(ProductFavorite::getUserId, userId)
                        .orderByDesc(ProductFavorite::getCreateTime));

        // 批量查商品 + 商家
        Set<Long> productIds = favPage.getRecords().stream()
                .map(ProductFavorite::getProductId).collect(Collectors.toSet());
        if (productIds.isEmpty()) {
            return new Page<>(favPage.getCurrent(), favPage.getSize(), favPage.getTotal());
        }
        List<Product> products = productMapper.selectBatchIds(productIds);
        Set<Long> merchantIds = products.stream().map(Product::getMerchantId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> merchantMap = merchantIds.isEmpty() ? Collections.emptyMap()
                : merchantMapper.selectBatchIds(merchantIds).stream()
                        .collect(Collectors.toMap(Merchant::getId, Merchant::getMerchantName, (a, b) -> a));

        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));

        Page<Map<String, Object>> voPage = new Page<>(favPage.getCurrent(), favPage.getSize(), favPage.getTotal());
        List<Map<String, Object>> voRecords = new ArrayList<>();
        for (ProductFavorite fav : favPage.getRecords()) {
            Product p = productMap.get(fav.getProductId());
            if (p == null) continue; // 商品可能已被删除，跳过

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("favoriteId", fav.getId());
            item.put("favoritedAt", fav.getCreateTime());
            item.put("productId", p.getId());
            item.put("productName", p.getProductName());
            item.put("mainImage", p.getMainImage());
            item.put("unit", p.getUnit());
            item.put("status", p.getStatus());
            item.put("defaultSkuId", p.getDefaultSkuId());
            item.put("merchantId", p.getMerchantId());
            item.put("merchantName", merchantMap.getOrDefault(p.getMerchantId(), ""));
            voRecords.add(item);
        }
        voPage.setRecords(voRecords);
        return voPage;
    }

    /** 删除某条收藏 */
    public void remove(Long favoriteId) {
        Long userId = SecurityContext.getUserId();
        favoriteMapper.delete(new LambdaQueryWrapper<ProductFavorite>()
                .eq(ProductFavorite::getId, favoriteId)
                .eq(ProductFavorite::getUserId, userId));
    }

    /** 清空我的收藏 */
    public void clear() {
        Long userId = SecurityContext.getUserId();
        favoriteMapper.delete(new LambdaQueryWrapper<ProductFavorite>()
                .eq(ProductFavorite::getUserId, userId));
    }
}
