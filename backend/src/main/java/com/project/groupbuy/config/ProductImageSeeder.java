package com.project.groupbuy.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * V1.12: 商品图片自动填充器
 * 约束文档 29 章（禁止硬编码）+ 约束文档 11.2（商品必须有主图展示）
 *
 * 设计原则：
 *   1. 零硬编码 id / URL —— 对 product 表全量 WHERE main_image IS NULL 的行自动处理
 *   2. 确定性 seed —— 用 product.id 作为 picsum.photos 的 seed 参数，每个商品有固定但不同的图
 *   3. 幂等 —— 只 UPDATE IS NULL 的行，已填的跳过
 *   4. 可移植 —— 未来新增商家/商品自动命中下一次启动
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductImageSeeder implements CommandLineRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(String... args) {
        // 1. 找出所有 main_image 为空的商品 id
        Integer nullCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM product WHERE main_image IS NULL OR main_image = ''", Integer.class);
        if (nullCount == null || nullCount == 0) return;

        log.info("[V1.12] 发现 {} 个商品缺少主图，开始自动填充...", nullCount);

        // 2. 批量 UPDATE —— 用 product.id 作为 seed，picsum.photos 保证同一 id 返回同一张图
        jdbc.query(
            "SELECT id FROM product WHERE main_image IS NULL OR main_image = ''",
            rs -> {
                long id = rs.getLong("id");
                String imgUrl = "https://picsum.photos/seed/groupbuy-product-" + id + "/400/400";
                jdbc.update(
                    "UPDATE product SET main_image = ? WHERE id = ? AND (main_image IS NULL OR main_image = '')",
                    imgUrl, id);
            });

        log.info("[V1.12] ✅ 商品主图自动填充完成（本次处理 {} 条）", nullCount);
    }
}