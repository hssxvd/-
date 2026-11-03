package com.project.groupbuy.modules.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.project.groupbuy.common.context.SecurityContext;
import com.project.groupbuy.common.exception.BusinessException;
import com.project.groupbuy.common.result.ErrorCode;
import com.project.groupbuy.common.aspect.OpLog;
import com.project.groupbuy.modules.product.dto.ProductQuery;
import com.project.groupbuy.modules.product.entity.Category;
import com.project.groupbuy.modules.product.entity.Product;
import com.project.groupbuy.modules.product.entity.ProductAttribute;
import com.project.groupbuy.modules.product.entity.ProductSku;
import com.project.groupbuy.modules.product.entity.Merchant;
import com.project.groupbuy.modules.product.mapper.CategoryMapper;
import com.project.groupbuy.modules.product.mapper.MerchantMapper;
import com.project.groupbuy.modules.product.mapper.ProductAttributeMapper;
import com.project.groupbuy.modules.product.mapper.ProductMapper;
import com.project.groupbuy.modules.product.mapper.ProductSkuMapper;
import com.project.groupbuy.modules.product.entity.ProductSkuAttribute;
import com.project.groupbuy.modules.product.mapper.ProductSkuAttributeMapper;
import com.project.groupbuy.modules.supply.entity.Stock;
import com.project.groupbuy.modules.supply.mapper.StockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;
import com.project.groupbuy.common.constant.UserTypeConstant;

/**
 * 商品服务（约束文档 15.1 课题核心功能：分类检索与特色属性筛选）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    /**
     * 浏览历史内存存储（C4 个性化排序支撑）
     * 简化实现：ConcurrentHashMap<userId, 最近浏览的 productId 列表>
     * 生产环境应换 Redis
     */
    private static final java.util.Map<Long, java.util.Deque<Long>> VIEW_HISTORY = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int VIEW_HISTORY_MAX = 20;

    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ProductAttributeMapper productAttributeMapper;
    private final CategoryMapper categoryMapper;
    private final MerchantMapper merchantMapper;
    private final com.project.groupbuy.modules.order.mapper.OrderItemMapper orderItemMapper;
    private final com.project.groupbuy.modules.order.mapper.OrdersMapper ordersMapper;
    private final StockMapper stockMapper;
    private final ProductSkuAttributeMapper productSkuAttributeMapper;

    // ===== 分类树 =====

    /**
     * 分类树
     */
    public List<Category> categoryTree() {
        List<Category> all = categoryMapper.selectList(null);
        Map<Long, List<Category>> parentMap = all.stream()
                .collect(Collectors.groupingBy(c -> c.getParentId() == null ? 0L : c.getParentId()));
        List<Category> roots = parentMap.getOrDefault(0L, Collections.emptyList());
        roots.sort(Comparator.comparing(Category::getSortOrder, Comparator.nullsLast(Integer::compareTo)));
        return roots;
    }

    // ===== 分类检索（课题核心功能）=====

    /**
     * 分类检索商品（约束文档 15.1）
     * 支持：分类 + 特色属性筛选 + 价格区间 + 关键词 + 排序，可组合
     */
    public Page<Product> search(ProductQuery query) {
        Page<Product> page = new Page<>(query.getPageNum(), query.getPageSize());

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();

        // 0. 数据隔离（约束 8 + 30.5）
        //    MERCHANT：强制 merchant_id 过滤 + 看全状态（含 DRAFT/OFF_SHELF）
        //    其他角色 ：走 query 参数 + 只看 ON_SHELF
        String userType = SecurityContext.getUserType();
        Long mid = SecurityContext.getMerchantId();
        if (UserTypeConstant.MERCHANT.equals(userType)) {
            wrapper.eq(Product::getMerchantId, mid);
            // 商家端 status 允许 DRAFT/OFF_SHELF/ON_SHELF 全查
            if (StringUtils.hasText(query.getStatus())) {
                wrapper.eq(Product::getStatus, query.getStatus());
            }
        } else {
            // 消费者：只查上架
            wrapper.eq(Product::getStatus, "ON_SHELF");
        }

        // 2. 社区筛选
        if (query.getCommunityId() != null) {
            wrapper.and(w -> w.eq(Product::getCommunityId, query.getCommunityId())
                    .or().isNull(Product::getCommunityId));
        }

        // 3. 分类筛选（含子分类）
        if (query.getCategoryId() != null) {
            List<Long> catIds = getChildCategoryIds(query.getCategoryId());
            catIds.add(query.getCategoryId());
            wrapper.in(Product::getCategoryId, catIds);
        }

        // 4. 关键词搜索
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.like(Product::getProductName, query.getKeyword());
        }

        // 5. 特色属性筛选 → 先查 product_attribute 找 productId
        if (query.getAttrFilters() != null && !query.getAttrFilters().isEmpty()) {
            List<Long> productIds = null;
            for (ProductQuery.AttrFilter f : query.getAttrFilters()) {
                List<Long> ids = productAttributeMapper.selectList(
                        new LambdaQueryWrapper<ProductAttribute>()
                                .eq(ProductAttribute::getAttrKey, f.getKey())
                                .eq(ProductAttribute::getAttrValue, f.getValue()))
                        .stream().map(ProductAttribute::getProductId).collect(Collectors.toList());
                if (productIds == null) {
                    productIds = ids;
                } else {
                    productIds.retainAll(ids);
                }
            }
            if (productIds == null || productIds.isEmpty()) {
                return page; // 无匹配
            }
            wrapper.in(Product::getId, productIds);
        }

        // 6. 价格区间 → 用 SQL 子查询查 SKU 最低价格关联的 product_id（C10 性能修复）
        if (query.getPriceMin() != null || query.getPriceMax() != null) {
            StringBuilder sql = new StringBuilder(
                    "SELECT DISTINCT product_id FROM product_sku WHERE status='ACTIVE'");
            if (query.getPriceMin() != null) {
                sql.append(" AND price >= ").append(query.getPriceMin());
            }
            if (query.getPriceMax() != null) {
                sql.append(" AND price <= ").append(query.getPriceMax());
            }
            wrapper.inSql(Product::getId, sql.toString());
        }

        // 7. 排序
        applySort(wrapper, query.getSortBy(), query.getPersonalized());

        Page<Product> result = productMapper.selectPage(page, wrapper);

        // 8. 批量填充默认 SKU 价格（C13 修复：列表也显示价格，价格不冗余在 product 表）
        fillDefaultSkuPrice(result.getRecords());

        // 9. V1.11 批量填充 merchantName + categoryName（消费者首页/分类页展示商家信息）
        fillMerchantAndCategory(result.getRecords());

        // 10. 填充库存信息（约束文档 L1117：商品列表必须突出库存状态）
        fillStockInfo(result.getRecords(), query.getCommunityId());

        return result;
    }

    /**
     * 批量给 Product 列表填充默认 SKU 价格（最低价格的 ACTIVE SKU）
     */
    private void fillDefaultSkuPrice(List<Product> products) {
        if (products == null || products.isEmpty()) return;
        List<Long> productIds = products.stream().map(Product::getId).toList();
        List<ProductSku> allSkus = productSkuMapper.selectList(
                new LambdaQueryWrapper<ProductSku>()
                        .in(ProductSku::getProductId, productIds)
                        .eq(ProductSku::getStatus, "ACTIVE"));
        // 按 productId 分组，取每个 product 的最低 price
        Map<Long, ProductSku> minPriceMap = new HashMap<>();
        for (ProductSku sku : allSkus) {
            ProductSku existing = minPriceMap.get(sku.getProductId());
            if (existing == null || sku.getPrice().compareTo(existing.getPrice()) < 0) {
                minPriceMap.put(sku.getProductId(), sku);
            }
        }
        for (Product p : products) {
            ProductSku sku = minPriceMap.get(p.getId());
            if (sku != null) {
                p.setPrice(sku.getPrice());
                p.setDefaultSkuId(sku.getId());
            }
        }
    }

    /**
     * V1.11 批量填充 merchantName + categoryName
     * —— 前端消费者首页/分类页需要展示"来自老张蔬果"
     */
    private void fillMerchantAndCategory(List<Product> products) {
        if (products == null || products.isEmpty()) return;

        // 收集所有 merchantId / categoryId
        Set<Long> merchantIds = new HashSet<>();
        Set<Long> categoryIds = new HashSet<>();
        for (Product p : products) {
            if (p.getMerchantId() != null) merchantIds.add(p.getMerchantId());
            if (p.getCategoryId() != null) categoryIds.add(p.getCategoryId());
        }

        // 批量查 merchant
        Map<Long, String> merchantMap = new HashMap<>();
        if (!merchantIds.isEmpty()) {
            merchantMapper.selectBatchIds(merchantIds).forEach(m ->
                    merchantMap.put(m.getId(), m.getMerchantName()));
        }

        // 批量查 category
        Map<Long, String> categoryMap = new HashMap<>();
        if (!categoryIds.isEmpty()) {
            categoryMapper.selectBatchIds(categoryIds).forEach(c ->
                    categoryMap.put(c.getId(), c.getCategoryName()));
        }

        // 回填
        for (Product p : products) {
            p.setMerchantName(merchantMap.getOrDefault(p.getMerchantId(), "-"));
            p.setCategoryName(categoryMap.getOrDefault(p.getCategoryId(), "-"));
        }
    }

    /**
     * 填充库存信息（约束文档 L1117：商品列表必须突出库存状态）
     * 用 defaultSkuId 批量查 Stock，按 communityId 过滤
     *
     * 修复 V1：跨社区场景（communityId == null）取最大 available，
     *          避免"有社区有货但取了缺货社区导致全员已售罄"。
     *          精准社区场景（communityId != null）仍取最小（同社区多 pickup_point 兜底）。
     */
    private void fillStockInfo(List<Product> products, Long communityId) {
        if (products == null || products.isEmpty()) return;

        // 收集所有 defaultSkuId（已由 fillDefaultSkuPrice 填好）
        Set<Long> skuIds = new HashSet<>();
        for (Product p : products) {
            if (p.getDefaultSkuId() != null) skuIds.add(p.getDefaultSkuId());
        }
        if (skuIds.isEmpty()) return;

        // 批量查 Stock：sku_id + community_id（communityId 为空则不限）
        LambdaQueryWrapper<Stock> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Stock::getSkuId, skuIds);
        if (communityId != null) {
            wrapper.and(w -> w.eq(Stock::getCommunityId, communityId).or().isNull(Stock::getCommunityId));
        }
        List<Stock> stocks = stockMapper.selectList(wrapper);

        // 按 skuId 分组
        //   - 精准社区：取最小 available（同社区多 pickup_point 兜底）
        //   - 跨社区推荐：取最大 available（任一社区有货即视为有货）
        Map<Long, Stock> skuStockMap = new HashMap<>();
        for (Stock s : stocks) {
            Stock exist = skuStockMap.get(s.getSkuId());
            if (exist == null) {
                skuStockMap.put(s.getSkuId(), s);
            } else {
                int sAvail = s.getAvailableQty() == null ? 0 : s.getAvailableQty();
                int eAvail = exist.getAvailableQty() == null ? 0 : exist.getAvailableQty();
                boolean pickS;
                if (communityId != null) {
                    pickS = sAvail < eAvail;   // 精准 → 取小（最严格）
                } else {
                    pickS = sAvail > eAvail;   // 跨社区 → 取大（最宽松）
                }
                if (pickS) skuStockMap.put(s.getSkuId(), s);
            }
        }

        for (Product p : products) {
            if (p.getDefaultSkuId() == null) continue;
            Stock s = skuStockMap.get(p.getDefaultSkuId());
            int avail = (s == null || s.getAvailableQty() == null) ? 0 : s.getAvailableQty();
            p.setAvailableQty(avail);
            // warnStatus
            String ws;
            if (avail == 0) {
                ws = "EMPTY";
            } else if (s != null && s.getWarnQty() != null && avail <= s.getWarnQty()) {
                ws = "WARN";
            } else {
                ws = "NORMAL";
            }
            p.setWarnStatus(ws);
        }
    }

    private void applySort(LambdaQueryWrapper<Product> wrapper, String sortBy, Boolean personalized) {
        // C4 修复：个性化排序实际逻辑（约束文档 15.1：基于浏览偏好排序）
        if (Boolean.TRUE.equals(personalized) && SecurityContext.isLoggedIn()) {
            java.util.Deque<Long> viewed = VIEW_HISTORY.get(SecurityContext.getUserId());
            if (viewed != null && !viewed.isEmpty()) {
                String fieldOrder = viewed.stream()
                        .map(String::valueOf)
                        .collect(java.util.stream.Collectors.joining(","));
                // 浏览过的商品排前面，其余按 create_time DESC
                wrapper.last("ORDER BY FIELD(id, " + fieldOrder + ") DESC, create_time DESC");
                return;
            }
        }
        switch (sortBy == null ? "DEFAULT" : sortBy) {
            case "PRICE_ASC":
                wrapper.orderByAsc(Product::getId); // 价格排序需要 join SKU，简化用 ID
                break;
            case "PRICE_DESC":
                wrapper.orderByDesc(Product::getId);
                break;
            case "TIME":
                wrapper.orderByDesc(Product::getCreateTime);
                break;
            default:
                wrapper.orderByDesc(Product::getCreateTime);
        }
    }

    /**
     * 记录浏览历史（C4 个性化排序支撑）
     */
    private void recordView(Long productId) {
        if (!SecurityContext.isLoggedIn()) return;
        Long userId = SecurityContext.getUserId();
        java.util.Deque<Long> deque = VIEW_HISTORY.computeIfAbsent(userId, k -> new java.util.ArrayDeque<>());
        // 去重后加到队头
        deque.remove(productId);
        deque.addFirst(productId);
        // 控制长度
        while (deque.size() > VIEW_HISTORY_MAX) {
            deque.removeLast();
        }
    }

    /**
     * 最近浏览的商品（前端 Home 页"你最近看过"区块）
     * 返回完整 Product（含 price / availableQty / warnStatus / merchantName）
     */
    public List<Product> getRecentViewed(Long communityId) {
        if (!SecurityContext.isLoggedIn()) return List.of();
        java.util.Deque<Long> deque = VIEW_HISTORY.get(SecurityContext.getUserId());
        if (deque == null || deque.isEmpty()) return List.of();

        // 查 Product（保持浏览历史顺序）
        List<Product> products = productMapper.selectBatchIds(List.copyOf(deque));
        if (products.isEmpty()) return products;

        // 复用现有 helper 填充 transient 字段
        fillDefaultSkuPrice(products);
        fillMerchantAndCategory(products);
        fillStockInfo(products, communityId);

        return products;
    }

    private List<Long> getChildCategoryIds(Long parentId) {
        List<Long> ids = new ArrayList<>();
        List<Category> children = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().eq(Category::getParentId, parentId));
        for (Category c : children) {
            ids.add(c.getId());
            ids.addAll(getChildCategoryIds(c.getId()));
        }
        return ids;
    }

    // ===== 商品详情 =====

    /**
     * 商品详情（按社区查询库存）
     * 约束文档 4.5：返回商品 + SKU + 特色属性 + 按 communityId 聚合的库存
     * 约束文档 11.2：详情页必须展示库存、规格、价格
     */
    public Map<String, Object> detail(Long productId, Long communityId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "商品不存在");
        }

        if (product.getMerchantId() != null) {
            Merchant m = merchantMapper.selectById(product.getMerchantId());
            if (m != null) product.setMerchantName(m.getMerchantName());
        }
        if (product.getCategoryId() != null) {
            Category c = categoryMapper.selectById(product.getCategoryId());
            if (c != null) product.setCategoryName(c.getCategoryName());
        }
        recordView(productId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("product", product);

        // SKU 列表
        List<ProductSku> skus = productSkuMapper.selectList(
                new LambdaQueryWrapper<ProductSku>()
                    .eq(ProductSku::getProductId, productId)
                    .eq(ProductSku::getIsDeleted, 0));

        if (skus != null && !skus.isEmpty()) {
            List<Long> skuIds = skus.stream().map(ProductSku::getId).collect(Collectors.toList());

            // 批量查 stock（按 communityId）
            List<Stock> stocks = stockMapper.selectList(
                    new LambdaQueryWrapper<Stock>()
                        .in(Stock::getSkuId, skuIds)
                        .eq(Stock::getCommunityId, communityId)
                        .eq(Stock::getIsDeleted, 0));
            Map<Long, Stock> stockMap = stocks.stream()
                    .collect(Collectors.toMap(Stock::getSkuId, s -> s, (a, b) -> a));

            // 批量查 sku_attrs
            List<ProductSkuAttribute> allAttrsFlat = productSkuAttributeMapper.selectList(
                    new LambdaQueryWrapper<ProductSkuAttribute>()
                        .in(ProductSkuAttribute::getSkuId, skuIds)
                        .eq(ProductSkuAttribute::getIsDeleted, 0));
            Map<Long, List<ProductSkuAttribute>> attrMap = allAttrsFlat.stream()
                    .collect(Collectors.groupingBy(ProductSkuAttribute::getSkuId));

            for (ProductSku sku : skus) {
                Stock s = stockMap.get(sku.getId());
                sku.setAvailableQty(s != null ? s.getAvailableQty() : 0);
                sku.setWarnQty(s != null ? s.getWarnQty() : null);
                sku.setSkuAttrs(attrMap.getOrDefault(sku.getId(), Collections.emptyList()));
            }
        }
        result.put("skus", skus);
        result.put("communityId", communityId);

        // 特色属性（约束文档 4.5）
        List<ProductAttribute> allAttrs = productAttributeMapper.selectList(
                new LambdaQueryWrapper<ProductAttribute>().eq(ProductAttribute::getProductId, productId)
                        .orderByAsc(ProductAttribute::getSortOrder));
        result.put("attributes", allAttrs);

        List<ProductAttribute> highlights = allAttrs.stream()
                .filter(a -> Integer.valueOf(1).equals(a.getIsHighlight()))
                .collect(Collectors.toList());
        result.put("highlights", highlights);

        return result;
    }

    // ===== 商家商品 CRUD =====

    /**
     * 商家创建商品
     */
    @Transactional(rollbackFor = Exception.class)
    public Product create(Product product) {
        Long merchantId = SecurityContext.getMerchantId();
        product.setMerchantId(merchantId);
        product.setStatus("DRAFT");
        productMapper.insert(product);
        return product;
    }

    /**
     * 商家编辑商品
     *   DRAFT / OFF_SHELF  → 全部字段可编辑
     *   ON_SHELF          → 仅允许修改展示字段（mainImage/images/description），
     *                       交易字段（price/unit/categoryId 等）禁止修改
     * 约束文档 §5.1：上架中商品可被浏览/加购/下单，改展示图不影响交易，改价格/分类会影响已有订单快照
     */
    @Transactional(rollbackFor = Exception.class)
    public Product update(Long id, Product update) {
        Product existing = productMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        checkMerchantOwns(existing);

        if ("ON_SHELF".equals(existing.getStatus())) {
            // 字段级校验：ON_SHELF 仅允许修改展示字段（仅校验前端明确传了非 null 的字段）
            List<String> forbiddenFields = new ArrayList<>();
            if (update.getPrice() != null        && !Objects.equals(existing.getPrice(), update.getPrice()))          forbiddenFields.add("价格");
            if (update.getUnit() != null         && !Objects.equals(existing.getUnit(), update.getUnit()))            forbiddenFields.add("单位");
            if (update.getCategoryId() != null   && !Objects.equals(existing.getCategoryId(), update.getCategoryId()))forbiddenFields.add("分类");
            if (update.getProductName() != null  && !Objects.equals(existing.getProductName(), update.getProductName()))forbiddenFields.add("商品名称");
            if (update.getDefaultSkuId() != null && !Objects.equals(existing.getDefaultSkuId(), update.getDefaultSkuId()))forbiddenFields.add("默认SKU");
            if (update.getStatus() != null       && !Objects.equals(existing.getStatus(), update.getStatus()))        forbiddenFields.add("状态(请用下架接口)");
            if (update.getMerchantId() != null   && !Objects.equals(existing.getMerchantId(), update.getMerchantId()))forbiddenFields.add("商家");
            if (update.getCommunityId() != null  && !Objects.equals(existing.getCommunityId(), update.getCommunityId()))forbiddenFields.add("社区");
            if (update.getProductCode() != null  && !Objects.equals(existing.getProductCode(), update.getProductCode()))forbiddenFields.add("商品编码");
            if (update.getDeadlineTime() != null && !Objects.equals(existing.getDeadlineTime(), update.getDeadlineTime()))forbiddenFields.add("截止时间");

            if (!forbiddenFields.isEmpty()) {
                throw new BusinessException(ErrorCode.PRODUCT_OFF_SHELF,
                        "上架中商品仅可修改主图/描述，禁止修改：" + String.join("、", forbiddenFields) + "（请先下架再编辑）");
            }
            // 明确清空不允许的字段，避免 MyBatis-Plus 把 null/原值也更新上去（虽然值没差，但显式安全）
            update.setPrice(null); update.setUnit(null); update.setCategoryId(null);
            update.setProductName(null); update.setDefaultSkuId(null);
            update.setStatus(null); update.setMerchantId(null); update.setCommunityId(null);
            update.setProductCode(null); update.setDeadlineTime(null);
            // 只保留允许修改的展示字段：mainImage / images / description
        }

        update.setId(id);
        productMapper.updateById(update);
        return productMapper.selectById(id);
    }

    /**
     * 上架（约束文档 5.1：校验 SKU 有库存）
     */
    @Transactional(rollbackFor = Exception.class)
    @OpLog(module = "商品", operationType = "UPDATE")
    public Product shelf(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        checkMerchantOwns(product);
        // C2 修复：校验 SKU 有库存
        List<ProductSku> skus = productSkuMapper.selectList(
                new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getProductId, id));
        boolean hasStock = skus.stream().anyMatch(s -> "ACTIVE".equals(s.getStatus()));
        if (skus.isEmpty() || !hasStock) {
            throw new BusinessException(ErrorCode.PRODUCT_OFF_SHELF, "商品无可用 SKU，无法上架");
        }
        product.setStatus("ON_SHELF");
        productMapper.updateById(product);
        return product;
    }

    /**
     * 下架（约束文档 5.1：校验无未完成订单）
     */
    @Transactional(rollbackFor = Exception.class)
    @OpLog(module = "商品", operationType = "UPDATE")
    public Product offShelf(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        checkMerchantOwns(product);
        // C3 修复：校验无未完成订单（C12 性能修复：用 inSql 子查询，避免全量加载）
        List<ProductSku> skus = productSkuMapper.selectList(
                new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getProductId, id));
        if (!skus.isEmpty()) {
            List<Long> skuIds = skus.stream().map(ProductSku::getId).toList();
            // 用 SQL 子查询直接在 DB 层完成关联过滤，避免 selectList(null) 全量加载
            Long pendingCount = orderItemMapper.selectCount(
                    new LambdaQueryWrapper<com.project.groupbuy.modules.order.entity.OrderItem>()
                            .in(com.project.groupbuy.modules.order.entity.OrderItem::getSkuId, skuIds)
                            .inSql(com.project.groupbuy.modules.order.entity.OrderItem::getOrderId,
                                    "SELECT id FROM orders WHERE order_status IN ('PENDING_PAY','PAID','FULFILLING')"));
            if (pendingCount > 0) {
                throw new BusinessException(ErrorCode.PRODUCT_HAS_ORDER, "商品有未完成订单，无法下架");
            }
        }
        product.setStatus("OFF_SHELF");
        productMapper.updateById(product);
        return product;
    }

    /**
     * 删除商品（逻辑删除）
     */
    public void delete(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        checkMerchantOwns(product);
        productMapper.deleteById(id);
    }

    private void checkMerchantOwns(Product product) {
        String userType = SecurityContext.getUserType();
        if (UserTypeConstant.MERCHANT.equals(userType)) {
            Long merchantId = SecurityContext.getMerchantId();
            if (!merchantId.equals(product.getMerchantId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }
    }
}
