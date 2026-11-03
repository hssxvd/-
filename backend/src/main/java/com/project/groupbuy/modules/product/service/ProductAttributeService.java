package com.project.groupbuy.modules.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.project.groupbuy.common.exception.BusinessException;
import com.project.groupbuy.common.result.ErrorCode;
import com.project.groupbuy.modules.product.entity.ProductAttribute;
import com.project.groupbuy.modules.product.mapper.ProductAttributeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * 商品特色属性服务（约束文档 4.5 课题核心：预定义字典 + is_filterable/is_highlight）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductAttributeService {

    private final ProductAttributeMapper productAttributeMapper;

    /**
     * 预定义 attr_key 字典（约束文档 4.5）
     */
    public static final List<String> ATTR_KEY_DICT = Arrays.asList(
            "产地", "品牌", "保质期", "规格", "重量", "适用人群", "特色标签"
    );

    /**
     * 校验 attr_key 是否在预定义字典中
     */
    public void validateAttrKey(String attrKey) {
        if (!ATTR_KEY_DICT.contains(attrKey)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "attr_key 不在预定义字典中，允许值：" + ATTR_KEY_DICT);
        }
    }

    /**
     * 新增特色属性
     */
    @Transactional(rollbackFor = Exception.class)
    public ProductAttribute create(ProductAttribute attr) {
        validateAttrKey(attr.getAttrKey());
        if (attr.getIsFilterable() == null) attr.setIsFilterable(0);
        if (attr.getIsHighlight() == null) attr.setIsHighlight(0);
        if (attr.getSortOrder() == null) attr.setSortOrder(0);
        productAttributeMapper.insert(attr);
        return attr;
    }

    /**
     * 批量设置特色属性（先删后插）
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchSet(Long productId, List<ProductAttribute> attrs) {
        // 校验所有 attr_key
        attrs.forEach(a -> {
            validateAttrKey(a.getAttrKey());
            a.setProductId(productId);
        });
        // 先删
        productAttributeMapper.delete(new LambdaQueryWrapper<ProductAttribute>()
                .eq(ProductAttribute::getProductId, productId));
        // 再插
        for (ProductAttribute a : attrs) {
            if (a.getIsFilterable() == null) a.setIsFilterable(0);
            if (a.getIsHighlight() == null) a.setIsHighlight(0);
            if (a.getSortOrder() == null) a.setSortOrder(0);
            productAttributeMapper.insert(a);
        }
    }

    /**
     * 按商品查询特色属性
     */
    public List<ProductAttribute> listByProduct(Long productId) {
        return productAttributeMapper.selectList(
                new LambdaQueryWrapper<ProductAttribute>()
                        .eq(ProductAttribute::getProductId, productId)
                        .orderByAsc(ProductAttribute::getSortOrder));
    }

    /**
     * 分类检索可用的筛选属性（is_filterable=1，去重）
     */
    public List<String> filterableAttrKeys() {
        return productAttributeMapper.selectList(
                new LambdaQueryWrapper<ProductAttribute>()
                        .eq(ProductAttribute::getIsFilterable, 1)
                        .select(ProductAttribute::getAttrKey)
                        .groupBy(ProductAttribute::getAttrKey))
                .stream().map(ProductAttribute::getAttrKey).distinct().toList();
    }

    /**
     * 按 attr_key 查可选值（用于筛选器下拉）
     */
    public List<String> valuesByKey(String attrKey) {
        return productAttributeMapper.selectList(
                new LambdaQueryWrapper<ProductAttribute>()
                        .eq(ProductAttribute::getAttrKey, attrKey)
                        .select(ProductAttribute::getAttrValue)
                        .groupBy(ProductAttribute::getAttrValue))
                .stream().map(ProductAttribute::getAttrValue).distinct().toList();
    }

    /**
     * 删除特色属性
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        productAttributeMapper.deleteById(id);
    }
}
