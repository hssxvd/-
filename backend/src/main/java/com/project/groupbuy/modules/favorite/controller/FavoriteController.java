package com.project.groupbuy.modules.favorite.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.project.groupbuy.common.result.R;
import com.project.groupbuy.modules.favorite.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 消费者端 · 商品收藏
 */
@Slf4j
@RestController
@RequestMapping("/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    /**
     * 切换收藏状态（加/删同接口，前端不用先查后调）
     * 返回 true=已收藏  false=已取消
     */
    @PostMapping("/toggle")
    public R<Boolean> toggle(@RequestParam Long productId) {
        return R.ok(favoriteService.toggle(productId));
    }

    /** 查单个商品是否已收藏 */
    @GetMapping("/status")
    public R<Boolean> status(@RequestParam Long productId) {
        return R.ok(favoriteService.isFavorited(productId));
    }

    /** 我的收藏列表（含商品详情 + 商家名） */
    @GetMapping
    public R<Page<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(favoriteService.listMine(pageNum, pageSize));
    }

    /** 删除单条收藏 */
    @DeleteMapping("/item/{id}")
    public R<Void> remove(@PathVariable Long id) {
        favoriteService.remove(id);
        return R.ok();
    }

    /** 清空收藏 */
    @DeleteMapping
    public R<Void> clear() {
        favoriteService.clear();
        return R.ok();
    }
}
