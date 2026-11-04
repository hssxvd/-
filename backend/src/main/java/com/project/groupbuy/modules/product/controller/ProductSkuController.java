package com.project.groupbuy.modules.product.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.project.groupbuy.common.result.R;
import com.project.groupbuy.modules.product.entity.ProductSku;
import com.project.groupbuy.modules.product.mapper.ProductSkuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * SKU 控制器（商家端创建预售活动时拉 SKU 下拉用）
 */
@RestController
@RequestMapping("/product-skus")
@RequiredArgsConstructor
public class ProductSkuController {

    private final ProductSkuMapper skuMapper;

    @GetMapping
    public R<Page<ProductSku>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "2000") int size,
            @RequestParam(required = false) Long productId) {
        Page<ProductSku> p = new Page<>(page, size);
        LambdaQueryWrapper<ProductSku> q = new LambdaQueryWrapper<>();
        if (productId != null) {
            q.eq(ProductSku::getProductId, productId);
        }
        q.orderByAsc(ProductSku::getProductId);
        return R.ok(skuMapper.selectPage(p, q));
    }
}