package com.project.groupbuy.modules.admin.service;

import lombok.extern.slf4j.Slf4j;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.project.groupbuy.common.annotation.RequirePermission;
import com.project.groupbuy.common.aspect.OpLog;
import com.project.groupbuy.common.constant.OrderStatusConstant;
import com.project.groupbuy.common.exception.BusinessException;
import com.project.groupbuy.common.result.ErrorCode;
import com.project.groupbuy.modules.product.entity.Merchant;
import com.project.groupbuy.modules.product.entity.Product;
import com.project.groupbuy.modules.product.mapper.MerchantMapper;
import com.project.groupbuy.modules.product.mapper.ProductMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理员端 · 全局商品审核管理
 * 约束文档 §23：跨社区管理商品；约束文档 §1141：管理员商品审核
 * 约束文档 §5.1 字段级权限（与商家端一致，但跳过 merchant 边界）
 */
@Slf4j
@Service
public class AdminProductService {

    private final ProductMapper productMapper;
    private final MerchantMapper merchantMapper;

    public AdminProductService(ProductMapper productMapper, MerchantMapper merchantMapper) {
        this.productMapper = productMapper;
        this.merchantMapper = merchantMapper;
    }

    /** 全局分页查询（超管无隔离，社区管理员只看自己 merchant 的商品） */
    public IPage<Map<String, Object>> page(int pageNum, int pageSize,
                                           String keyword, String status, Long merchantId, Long communityId) {
        Page<Product> page = new Page<>(Math.max(1, pageNum), Math.min(100, pageSize));
        LambdaQueryWrapper<Product> w = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(keyword)) {
            w.and(q -> q.like(Product::getProductName, keyword)
                    .or().like(Product::getProductCode, keyword));
        }
        if (StringUtils.hasText(status))        w.eq(Product::getStatus, status);
        if (merchantId != null)                  w.eq(Product::getMerchantId, merchantId);
        if (communityId != null)                 w.eq(Product::getCommunityId, communityId);
        w.orderByDesc(Product::getCreateTime);

        IPage<Product> pp = productMapper.selectPage(page, w);
        List<Product> records = pp.getRecords();

        // join merchantName
        Set<Long> mids = records.stream().map(Product::getMerchantId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> mMap = mids.isEmpty() ? Map.of() :
                merchantMapper.selectBatchIds(mids).stream().collect(Collectors.toMap(Merchant::getId, Merchant::getMerchantName, (a, b) -> a));

        Page<Map<String, Object>> result = new Page<>(pp.getCurrent(), pp.getSize(), pp.getTotal());
        result.setRecords(records.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("product", p);
            m.put("merchantName", mMap.get(p.getMerchantId()));
            return m;
        }).toList());
        return result;
    }

    /** 管理员修改商品（仅允许修改展示字段：mainImage / images / description） */
    @Transactional(rollbackFor = Exception.class)
    @OpLog(module = "商品", operationType = "UPDATE", targetType = "Product")
    public Product updateDisplay(Long id, Product update) {
        Product existing = productMapper.selectById(id);
        if (existing == null) throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "商品不存在");

        // 字段级校验（约束 §5.1：仅校验前端明确传了非 null 的字段）
        List<String> forbidden = new ArrayList<>();
        if (update.getPrice() != null        && !Objects.equals(existing.getPrice(), update.getPrice()))        forbidden.add("价格");
        if (update.getUnit() != null         && !Objects.equals(existing.getUnit(), update.getUnit()))          forbidden.add("单位");
        if (update.getCategoryId() != null   && !Objects.equals(existing.getCategoryId(), update.getCategoryId()))forbidden.add("分类");
        if (update.getProductName() != null  && !Objects.equals(existing.getProductName(), update.getProductName()))forbidden.add("商品名称");
        if (update.getDefaultSkuId() != null && !Objects.equals(existing.getDefaultSkuId(), update.getDefaultSkuId()))forbidden.add("默认SKU");
        if (update.getStatus() != null       && !Objects.equals(existing.getStatus(), update.getStatus()))        forbidden.add("状态(请用上架/下架接口)");
        if (update.getMerchantId() != null   && !Objects.equals(existing.getMerchantId(), update.getMerchantId()))forbidden.add("商家");
        if (update.getCommunityId() != null  && !Objects.equals(existing.getCommunityId(), update.getCommunityId()))forbidden.add("社区");
        if (update.getProductCode() != null  && !Objects.equals(existing.getProductCode(), update.getProductCode()))forbidden.add("商品编码");

        if (!forbidden.isEmpty()) {
            throw new BusinessException(ErrorCode.PRODUCT_OFF_SHELF,
                    "管理员仅可修改主图/描述，禁止修改：" + String.join("、", forbidden));
        }
        // 显式清空不允许的字段，避免 MyBatis-Plus 误更新
        update.setPrice(null); update.setUnit(null); update.setCategoryId(null);
        update.setProductName(null); update.setDefaultSkuId(null);
        update.setStatus(null); update.setMerchantId(null); update.setCommunityId(null);
        update.setProductCode(null); update.setDeadlineTime(null);

        update.setId(id);
        productMapper.updateById(update);
        return productMapper.selectById(id);
    }
}