package com.project.groupbuy.modules.product.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.project.groupbuy.common.result.R;
import com.project.groupbuy.modules.product.dto.ProductQuery;
import com.project.groupbuy.modules.product.entity.Category;
import com.project.groupbuy.modules.product.entity.Product;
import com.project.groupbuy.modules.product.entity.ProductAttribute;
import com.project.groupbuy.modules.product.service.ProductAttributeService;
import com.project.groupbuy.modules.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 商品控制器（约束文档 15.1：分类检索与个性化展示）
 */
@Slf4j
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductAttributeService attributeService;

    // ===== 公开接口（JWT 白名单）=====

    /**
     * 分类树
     */
    @GetMapping("/categories/tree")
    public R<List<Category>> categoryTree() {
        return R.ok(productService.categoryTree());
    }

    /**
     * 分类检索（课题核心功能）
     */
    @GetMapping
    public R<Page<Product>> search(ProductQuery query) {
        return R.ok(productService.search(query));
    }

    /**
     * 商品详情（按社区查库存）
     * 约束文档 4.5 / 11.2：SKU 库存必须按 communityId 隔离
     * communityId 必须传（前端强制用户先选社区）
     */
    @GetMapping("/{id:\\d+}")
    public R<Map<String, Object>> detail(@PathVariable Long id,
            @RequestParam Long communityId) {
        return R.ok(productService.detail(id, communityId));
    }

    /**
     * 特色属性预定义字典
     */
    @GetMapping("/attr-dict")
    public R<List<String>> attrDict() {
        return R.ok(ProductAttributeService.ATTR_KEY_DICT);
    }

    /**
     * 分类检索可用的筛选属性键
     */
    @GetMapping("/filterable-attr-keys")
    public R<List<String>> filterableAttrKeys() {
        return R.ok(attributeService.filterableAttrKeys());
    }

    /**
     * 某 attr_key 可选值
     */
    @GetMapping("/attr-values")
    public R<List<String>> attrValues(@RequestParam String key) {
        return R.ok(attributeService.valuesByKey(key));
    }

    // ===== 商家接口 =====

    /**
     * 创建商品
     */
    @PostMapping
    public R<Product> create(@RequestBody @Valid Product product) {
        return R.ok(productService.create(product));
    }

    /**
     * 编辑商品
     */
    @PutMapping("/{id:\\d+}")
    public R<Product> update(@PathVariable Long id, @RequestBody Product product) {
        return R.ok(productService.update(id, product));
    }

    /**
     * 上架
     */
    @PostMapping("/{id}/shelf")
    public R<Product> shelf(@PathVariable Long id) {
        return R.ok(productService.shelf(id));
    }

    /**
     * 下架
     */
    @PostMapping("/{id}/off-shelf")
    public R<Product> offShelf(@PathVariable Long id) {
        return R.ok(productService.offShelf(id));
    }

    /**
     * 删除
     */
    @DeleteMapping("/{id:\\d+}")
    public R<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return R.ok();
    }

    /**
     * 批量设置特色属性
     */
    @PostMapping("/{id}/attributes")
    public R<Void> setAttributes(@PathVariable Long id, @RequestBody List<ProductAttribute> attrs) {
        attributeService.batchSet(id, attrs);
        return R.ok();
    }

    /**
     * 最近浏览的商品（前端 Home 页"你最近看过"区块）
     * 返回完整 Product 列表（含 price / availableQty / warnStatus）
     */
    @GetMapping("/recent-viewed")
    public R<List<Product>> recentViewed(@RequestParam(required = false) Long communityId) {
        return R.ok(productService.getRecentViewed(communityId));
    }

    /**
     * 查询某商品特色属性
     */
    @GetMapping("/{id}/attributes")
    public R<List<ProductAttribute>> listAttributes(@PathVariable Long id) {
        return R.ok(attributeService.listByProduct(id));
    }
}
