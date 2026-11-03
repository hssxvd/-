package com.project.groupbuy.modules.admin.controller;

import lombok.extern.slf4j.Slf4j;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.project.groupbuy.common.annotation.RequirePermission;
import com.project.groupbuy.common.result.R;
import com.project.groupbuy.modules.admin.service.AdminProductService;
import com.project.groupbuy.modules.product.entity.Product;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理员端 · 全局商品审核
 * 约束文档 §23 + §1141：管理员可跨社区管理商品
 */
@Slf4j
@RestController
@RequestMapping("/admin/products")
public class AdminProductController {

    private final AdminProductService adminProductService;

    public AdminProductController(AdminProductService adminProductService) {
        this.adminProductService = adminProductService;
    }

    @GetMapping
    @RequirePermission("product:view")
    public R<IPage<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1")  int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) Long communityId) {
        return R.ok(adminProductService.page(pageNum, pageSize, keyword, status, merchantId, communityId));
    }



    @PutMapping("/{id}")
    @RequirePermission("product:admin")
    public R<Product> update(@PathVariable Long id, @RequestBody Product product) {
        return R.ok(adminProductService.updateDisplay(id, product));
    }
}