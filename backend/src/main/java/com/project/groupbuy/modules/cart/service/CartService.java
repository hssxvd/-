package com.project.groupbuy.modules.cart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.project.groupbuy.common.context.SecurityContext;
import com.project.groupbuy.common.exception.BusinessException;
import com.project.groupbuy.common.result.ErrorCode;
import com.project.groupbuy.modules.cart.entity.CartItem;
import com.project.groupbuy.modules.cart.mapper.CartItemMapper;
import com.project.groupbuy.modules.product.entity.Merchant;
import com.project.groupbuy.modules.product.entity.Product;
import com.project.groupbuy.modules.product.entity.ProductSku;
import com.project.groupbuy.modules.product.mapper.MerchantMapper;
import com.project.groupbuy.modules.product.mapper.ProductMapper;
import com.project.groupbuy.modules.product.mapper.ProductSkuMapper;
import com.project.groupbuy.modules.supply.entity.Stock;
import com.project.groupbuy.modules.supply.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 购物车服务（课题"购物车"）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemMapper cartItemMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ProductMapper productMapper;
    private final MerchantMapper merchantMapper;
    private final StockService stockService;

    /**
     * 加入购物车
     */
    @Transactional(rollbackFor = Exception.class)
    public CartItem add(Long skuId, Integer quantity, Long communityId) {
        Long userId = SecurityContext.getUserId();

        ProductSku sku = productSkuMapper.selectById(skuId);
        if (sku == null || !"ACTIVE".equals(sku.getStatus())) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "SKU 不存在或已下架");
        }

        // 1) 查当前可用库存（约束文档 6 + L32.6：available_qty 是唯一可用口径）
        Stock stock = stockService.getBySkuAndCommunity(skuId, communityId, null);
        int available = (stock == null || stock.getAvailableQty() == null) ? 0 : stock.getAvailableQty();
        if (available <= 0) {
            throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH, "该 SKU 已售罄");
        }
        if (quantity > available) {
            throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH,
                    String.format("库存不足，可用 %d，需要 %d", available, quantity));
        }

        // 2) 检查已有 — 累加后也要校验（约束文档 6：三重守卫）
        CartItem exist = cartItemMapper.selectOne(
                new LambdaQueryWrapper<CartItem>()
                        .eq(CartItem::getUserId, userId)
                        .eq(CartItem::getSkuId, skuId)
                        .last("LIMIT 1"));

        if (exist != null) {
            int newQty = exist.getQuantity() + quantity;
            if (newQty > available) {
                throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH,
                        String.format("加入后数量 %d 超过可用库存 %d", newQty, available));
            }
            exist.setQuantity(newQty);
            cartItemMapper.updateById(exist);
            return exist;
        }

        CartItem item = new CartItem();
        item.setUserId(userId);
        item.setSkuId(skuId);
        item.setProductId(sku.getProductId());
        item.setCommunityId(communityId);
        item.setQuantity(quantity);
        item.setPriceSnapshot(sku.getPrice());
        item.setSelected(1);
        cartItemMapper.insert(item);
        return item;
    }

    /**
     * 修改数量
     */
    @Transactional(rollbackFor = Exception.class)
    public CartItem updateQuantity(Long cartItemId, Integer quantity) {
        Long userId = SecurityContext.getUserId();
        CartItem item = cartItemMapper.selectById(cartItemId);
        if (item == null) throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "购物车项不存在");
        if (!item.getUserId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (quantity <= 0) {
            cartItemMapper.deleteById(cartItemId);
            return null;
        }
        // 校验调整后数量不超可用库存（约束文档 6）
        Stock stock = stockService.getBySkuAndCommunity(item.getSkuId(), item.getCommunityId(), null);
        int available = (stock == null) ? 0 : stock.getAvailableQty();
        if (available <= 0) {
            throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH, "该 SKU 已售罄");
        }
        if (quantity > available) {
            throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH,
                    String.format("库存不足，可用 %d，需要 %d", available, quantity));
        }
        item.setQuantity(quantity);
        cartItemMapper.updateById(item);
        return item;
    }

    /**
     * 选中/取消选中
     */
    @Transactional(rollbackFor = Exception.class)
    public CartItem updateSelected(Long cartItemId, Integer selected) {
        Long userId = SecurityContext.getUserId();
        CartItem item = cartItemMapper.selectById(cartItemId);
        if (item == null) throw new BusinessException(ErrorCode.DATA_NOT_FOUND);
        if (!item.getUserId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        item.setSelected(selected);
        cartItemMapper.updateById(item);
        return item;
    }

    /**
     * 全选/取消全选
     */
    @Transactional(rollbackFor = Exception.class)
    public void selectAll(Integer selected) {
        Long userId = SecurityContext.getUserId();
        cartItemMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<CartItem>()
                        .eq(CartItem::getUserId, userId)
                        .set(CartItem::getSelected, selected));
    }

    /**
     * 删除购物车项
     */
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long cartItemId) {
        Long userId = SecurityContext.getUserId();
        CartItem item = cartItemMapper.selectById(cartItemId);
        if (item == null) return;
        if (!item.getUserId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        cartItemMapper.deleteById(cartItemId);
    }

    /**
     * 清空购物车
     */
    @Transactional(rollbackFor = Exception.class)
    public void clear() {
        Long userId = SecurityContext.getUserId();
        cartItemMapper.delete(
                new LambdaQueryWrapper<CartItem>().eq(CartItem::getUserId, userId));
    }

    /**
     * 我的购物车列表（含商品名、SKU 名、库存、商家、价格快照对比）
     */
    public List<Map<String, Object>> listMine() {
        Long userId = SecurityContext.getUserId();
        List<CartItem> items = cartItemMapper.selectList(
                new LambdaQueryWrapper<CartItem>()
                        .eq(CartItem::getUserId, userId)
                        .orderByDesc(CartItem::getCreateTime));
        if (items.isEmpty()) return Collections.emptyList();

        // 批量查 SKU / Product / Merchant / Stock
        Set<Long> skuIds = items.stream().map(CartItem::getSkuId).collect(Collectors.toSet());
        Set<Long> productIds = items.stream().map(CartItem::getProductId).collect(Collectors.toSet());
        Set<Long> merchantIds = new HashSet<>();
        Map<Long, Product> productMap = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        productMap.values().forEach(p -> { if (p.getMerchantId() != null) merchantIds.add(p.getMerchantId()); });

        Map<Long, ProductSku> skuMap = productSkuMapper.selectBatchIds(skuIds).stream()
                .collect(Collectors.toMap(ProductSku::getId, s -> s));
        Map<Long, Merchant> merchantMap = merchantIds.isEmpty() ? Collections.emptyMap()
                : merchantMapper.selectBatchIds(merchantIds).stream()
                .collect(Collectors.toMap(Merchant::getId, m -> m));

        // Stock 批量查：skuId+communityId 联合 key
        Map<String, Stock> stockMap = new HashMap<>();
        for (CartItem ci : items) {
            Stock s = stockService.getBySkuAndCommunity(ci.getSkuId(), ci.getCommunityId(), null);
            if (s != null) stockMap.put(ci.getSkuId() + "_" + ci.getCommunityId(), s);
        }

        return items.stream().map(item -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", item.getId());
            m.put("userId", item.getUserId());
            m.put("skuId", item.getSkuId());
            m.put("productId", item.getProductId());
            m.put("communityId", item.getCommunityId());
            m.put("quantity", item.getQuantity() != null ? item.getQuantity() : 1);
            m.put("priceSnapshot", item.getPriceSnapshot() != null ? item.getPriceSnapshot() : BigDecimal.ZERO);
            m.put("selected", item.getSelected() != null ? item.getSelected() : 1);

            Product p = productMap.get(item.getProductId());
            ProductSku sku = skuMap.get(item.getSkuId());

            // 商品名 / 规格 / 单位
            m.put("productName", p != null && p.getProductName() != null ? p.getProductName() : "");
            m.put("skuName", sku != null && sku.getSkuName() != null ? sku.getSkuName() : "");
            m.put("unit", p != null && p.getUnit() != null ? p.getUnit() : "");
            m.put("merchantName", (p != null && p.getMerchantId() != null && merchantMap.containsKey(p.getMerchantId()))
                    ? merchantMap.get(p.getMerchantId()).getMerchantName() : "");

            // 图片：约束文档 L1115 — 实时 product.mainImage 优先，SKU image 兜底（图片口径统一）
            String img = "/placeholder-product.png";
            if (p != null && p.getMainImage() != null && !p.getMainImage().isBlank()) {
                img = p.getMainImage();
            } else if (sku != null && sku.getImage() != null && !sku.getImage().isBlank()) {
                img = sku.getImage();
            }
            m.put("image", img);

            // 当前价 + 价格变化标记
            BigDecimal currentPrice = sku != null && sku.getPrice() != null ? sku.getPrice() : BigDecimal.ZERO;
            m.put("currentPrice", currentPrice);
            boolean priceChanged = item.getPriceSnapshot() != null
                    && item.getPriceSnapshot().compareTo(currentPrice) != 0;
            m.put("priceChanged", priceChanged);

            // 库存状态：约束文档 6 / L32.6 — available_qty 为唯一可用口径
            Stock s = stockMap.get(item.getSkuId() + "_" + item.getCommunityId());
            int available = (s == null || s.getAvailableQty() == null) ? 0 : s.getAvailableQty();
            Integer warnQty = s != null ? s.getWarnQty() : null;
            m.put("availableQty", available);
            // warnStatus: EMPTY > WARN > NORMAL（EMPTY 优先）
            String warnStatus;
            if (available == 0) {
                warnStatus = "EMPTY";
            } else if (warnQty != null && available <= warnQty) {
                warnStatus = "WARN";
            } else {
                warnStatus = "NORMAL";
            }
            m.put("warnStatus", warnStatus);

            return m;
        }).collect(Collectors.toList());
    }

    /**
     * 选中项（用于下单前校验库存）
     */
    public List<CartItem> listSelected() {
        Long userId = SecurityContext.getUserId();
        return cartItemMapper.selectList(
                new LambdaQueryWrapper<CartItem>()
                        .eq(CartItem::getUserId, userId)
                        .eq(CartItem::getSelected, 1));
    }

    /**
     * 清空选中项（下单成功后调用）
     * 约束 28.19: 必须硬删除，不能用 delete()（@TableLogic 会转成软删除撞 uk_user_sku 唯一索引）
     */
    @Transactional(rollbackFor = Exception.class)
    public void clearSelected() {
        Long userId = SecurityContext.getUserId();
        cartItemMapper.hardDeleteSelected(userId);
    }

    /**
     * 购物车总价（选中项）
     */
    public BigDecimal totalPrice() {
        return listSelected().stream()
                .map(i -> i.getPriceSnapshot().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
