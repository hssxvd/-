package com.project.groupbuy.modules.cart.controller;

import com.project.groupbuy.common.result.R;
import com.project.groupbuy.modules.cart.entity.CartItem;
import com.project.groupbuy.modules.cart.service.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 购物车控制器（课题"购物车"）
 */
@Slf4j
@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    /**
     * 加入购物车
     */
    @PostMapping
    public R<CartItem> add(@RequestParam Long skuId,
                            @RequestParam Integer quantity,
                            @RequestParam Long communityId) {
        return R.ok(cartService.add(skuId, quantity, communityId));
    }

    /**
     * 修改数量
     */
    @PutMapping("/{id}/quantity")
    public R<CartItem> updateQuantity(@PathVariable Long id, @RequestParam Integer quantity) {
        return R.ok(cartService.updateQuantity(id, quantity));
    }

    /**
     * 选中/取消选中
     */
    @PutMapping("/{id}/selected")
    public R<CartItem> updateSelected(@PathVariable Long id, @RequestParam Integer selected) {
        return R.ok(cartService.updateSelected(id, selected));
    }

    /**
     * 全选/取消全选
     */
    @PutMapping("/select-all")
    public R<Void> selectAll(@RequestParam Integer selected) {
        cartService.selectAll(selected);
        return R.ok();
    }

    /**
     * 删除购物车项
     */
    @DeleteMapping("/{id:\\d+}")
    public R<Void> remove(@PathVariable Long id) {
        cartService.remove(id);
        return R.ok();
    }

    /**
     * 清空购物车
     */
    @DeleteMapping
    public R<Void> clear() {
        cartService.clear();
        return R.ok();
    }

    /**
     * 我的购物车（含商品名/SKU名/图片）
     */
    @GetMapping
    public R<List<Map<String, Object>>> list() {
        return R.ok(cartService.listMine());
    }

    /**
     * 购物车总价（选中项）
     */
    @GetMapping("/total")
    public R<BigDecimal> total() {
        return R.ok(cartService.totalPrice());
    }
}
