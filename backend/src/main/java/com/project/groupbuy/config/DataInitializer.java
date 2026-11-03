package com.project.groupbuy.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.project.groupbuy.modules.community.entity.Community;
import com.project.groupbuy.modules.community.mapper.CommunityMapper;
import com.project.groupbuy.modules.community.entity.PickupPoint;
import com.project.groupbuy.modules.community.mapper.PickupPointMapper;
import com.project.groupbuy.modules.product.entity.Category;
import com.project.groupbuy.modules.product.entity.Merchant;
import com.project.groupbuy.modules.product.entity.Product;
import com.project.groupbuy.modules.product.entity.ProductAttribute;
import com.project.groupbuy.modules.product.entity.ProductSku;
import com.project.groupbuy.modules.product.mapper.CategoryMapper;
import com.project.groupbuy.modules.product.mapper.MerchantMapper;
import com.project.groupbuy.modules.product.mapper.ProductAttributeMapper;
import com.project.groupbuy.modules.product.mapper.ProductMapper;
import com.project.groupbuy.modules.product.mapper.ProductSkuMapper;
import com.project.groupbuy.modules.supply.entity.Stock;
import com.project.groupbuy.modules.supply.mapper.StockMapper;
import com.project.groupbuy.modules.groupbuy.entity.GroupBuyActivity;
import com.project.groupbuy.modules.groupbuy.mapper.GroupBuyActivityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 种子数据初始化器（幂等）
 * - 启动时自动补齐 community / merchant / category / product / sku / attribute / stock
 * - 种子数量目标：community 4 / merchant 5 / category 12 / product 60 / sku 120+ / attribute 300+
 * - 约束对照：L29 模拟数据 / L292 特色属性预定义字典 / L274 订单快照
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final CommunityMapper communityMapper;
    private final PickupPointMapper pickupPointMapper;
    private final MerchantMapper merchantMapper;
    private final CategoryMapper categoryMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ProductAttributeMapper productAttributeMapper;
    private final StockMapper stockMapper;
    private final GroupBuyActivityMapper groupBuyActivityMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(String... args) {
        // 1. 社区补齐 → 4 个
        long communityCount = communityMapper.selectCount(null);
        if (communityCount < 4) seedCommunities();

        // 2. 商家补齐 → 5 个
        long merchantCount = merchantMapper.selectCount(null);
        if (merchantCount < 5) seedMerchants();

        // 3. 分类补齐 → 12 个（3 根 + 9 子）
        long categoryCount = categoryMapper.selectCount(null);
        if (categoryCount < 12) seedCategories();

        // 4. 自提点：为新 community 补 pickup_point
        seedPickupPoints();

        // 5. 商品 + SKU + 属性 + 库存 → 至少 60 商品
        long productCount = productMapper.selectCount(null);
        if (productCount < 50) {
            // 已有商品尽量复用其 category/merchant，补到 60
            List<Merchant> merchants = merchantMapper.selectList(null);
            List<Category> categories = categoryMapper.selectList(null);
            List<Community> communities = communityMapper.selectList(null);
            List<PickupPoint> pickupPoints = pickupPointMapper.selectList(null);

            int target = 60;
            int existing = (int) productCount;
            log.info("[种子] 现有商品 {}，目标 {}，需补 {} 个", existing, target, target - existing);

            for (int i = existing; i < target; i++) {
                Merchant m = merchants.get(i % merchants.size());
                Category c = categories.stream().filter(cat -> cat.getParentId() != null)
                        .skip(i % Math.max(1, categories.size() - 3)).findFirst()
                        .orElse(categories.get(categories.size() - 1));
                Community comm = communities.get(i % communities.size());

                createProductWithSkuAttrStock(m, c, comm, pickupPoints, i);
            }
            log.info("[种子] 商品扩容完成 → {} 个", productMapper.selectCount(null));
        }

        // 6. 团购活动种子 → 10 条 OPEN 状态
        long gbCount = groupBuyActivityMapper.selectCount(null);
        if (gbCount < 10) seedGroupBuyActivities();
    }

    // ==================== 种子数据方法 ====================

    private void seedCommunities() {
        List<Community> seed = List.of(
                mkCommunity("浦东张江社区", "PUDONG_ZJ", "浦东新区张江高科技园区", "李阿姨", "1380000****"),
                mkCommunity("浦东联洋社区", "PUDONG_LY", "浦东新区联洋国际社区", "王阿姨", "1390000****")
        );
        for (Community c : seed) {
            Community existing = communityMapper.selectOne(
                    new LambdaQueryWrapper<Community>().eq(Community::getCommunityCode, c.getCommunityCode()));
            if (existing == null) { communityMapper.insert(c); }
        }
    }

    private void seedMerchants() {
        List<Merchant> seed = List.of(
                mkMerchant("鲜蔬果品店", "FRESH03", "张老板", "1370000****"),
                mkMerchant("崇明粮油直销", "GRAIN04", "赵老板", "1360000****"),
                mkMerchant("烘焙小作坊", "BAKE05", "陈老板", "1350000****")
        );
        for (Merchant m : seed) {
            Merchant existing = merchantMapper.selectOne(
                    new LambdaQueryWrapper<Merchant>().eq(Merchant::getMerchantCode, m.getMerchantCode()));
            if (existing == null) { merchantMapper.insert(m); }
        }
    }

    private void seedCategories() {
        List<Category> roots = List.of(
                mkCategory(null, "时令生鲜", "FRESH_SEASON", 1, "🥬"),
                mkCategory(null, "粮油副食", "GRAIN_OIL",    2, "🌾"),
                mkCategory(null, "烘焙点心", "BAKE_SNACK",   3, "🍞")
        );
        Map<String, Category> rootByName = new HashMap<>();
        for (Category r : roots) {
            Category existing = categoryMapper.selectOne(
                    new LambdaQueryWrapper<Category>().eq(Category::getCategoryCode, r.getCategoryCode()));
            if (existing == null) { categoryMapper.insert(r); rootByName.put(r.getCategoryCode(), r); }
            else rootByName.put(r.getCategoryCode(), existing);
        }
        // 子分类
        List<Category> subs = List.of(
                mkCategory(rootByName.get("FRESH_SEASON").getId(), "有机蔬菜", "VEG_ORGANIC",  1, "🥬"),
                mkCategory(rootByName.get("FRESH_SEASON").getId(), "时令水果", "FRUIT_SEASON", 2, "🍎"),
                mkCategory(rootByName.get("FRESH_SEASON").getId(), "肉蛋家禽", "MEAT_EGG",     3, "🥩"),
                mkCategory(rootByName.get("GRAIN_OIL").getId(),    "米面粮油", "STAPLE_GRAIN", 1, "🌾"),
                mkCategory(rootByName.get("GRAIN_OIL").getId(),    "干货调料", "DRY_SEASON",   2, "🧂"),
                mkCategory(rootByName.get("GRAIN_OIL").getId(),    "乳制品",   "DAIRY",        3, "🥛"),
                mkCategory(rootByName.get("BAKE_SNACK").getId(),   "面包吐司", "BREAD_TOAST",  1, "🍞"),
                mkCategory(rootByName.get("BAKE_SNACK").getId(),   "蛋糕甜品", "CAKE_DESSERT", 2, "🍰"),
                mkCategory(rootByName.get("BAKE_SNACK").getId(),   "休闲零食", "SNACK",        3, "🍪")
        );
        for (Category c : subs) {
            Category existing = categoryMapper.selectOne(
                    new LambdaQueryWrapper<Category>().eq(Category::getCategoryCode, c.getCategoryCode()));
            if (existing == null) { categoryMapper.insert(c); }
        }
    }

    private void seedPickupPoints() {
        for (Community c : communityMapper.selectList(null)) {
            long cnt = pickupPointMapper.selectCount(
                    new LambdaQueryWrapper<PickupPoint>().eq(PickupPoint::getCommunityId, c.getId()));
            if (cnt == 0) {
                PickupPoint p = new PickupPoint();
                p.setCommunityId(c.getId());
                p.setPointName(c.getCommunityName() + "自提点");
                p.setAddress(c.getAddress());
                p.setOpenTime("08:00-20:00");
                p.setStatus("ACTIVE");
                pickupPointMapper.insert(p);
            }
        }
    }

    /** 每商品：Product + 2 SKU + 3-5 ProductAttribute + (community × pickup_point) 条 Stock */
    private void createProductWithSkuAttrStock(Merchant m, Category subCat, Community comm,
                                                List<PickupPoint> allPoints, int seq) {
        String[] names;
        String[] descriptions;
        String unit;
        switch (subCat.getCategoryCode()) {
            case "VEG_ORGANIC":
                names = new String[]{"有机崇明青菜", "有机崇明菠菜"};
                descriptions = new String[]{"崇明岛大棚有机蔬菜，当日直达", "有机深绿菠菜，清炒最佳"};
                unit = "斤"; break;
            case "FRUIT_SEASON":
                names = new String[]{"烟台红富士苹果", "浙江临海蜜桔"};
                descriptions = new String[]{"脆甜多汁，栖霞产区", "皮薄汁多，涌泉蜜桔"};
                unit = "斤"; break;
            case "MEAT_EGG":
                names = new String[]{"土鸡蛋 30 枚装", "崇明散养草鸡 1 只"};
                descriptions = new String[]{"散养土鸡蛋，蛋黄橙红", "当日现杀草鸡约 2 斤"};
                unit = "份"; break;
            case "STAPLE_GRAIN":
                names = new String[]{"崇明优质大米 5kg", "东北珍珠米 10kg"};
                descriptions = new String[]{"崇明原产，软糯清香", "黑龙江圆粒珍珠米"};
                unit = "袋"; break;
            case "DRY_SEASON":
                names = new String[]{"绍兴花雕酒 500ml", "农家自制辣椒酱"};
                descriptions = new String[]{"十年陈酿花雕", "手工剁辣椒，下饭神器"};
                unit = "瓶"; break;
            case "DAIRY":
                names = new String[]{"光明鲜牛奶 1L", "崇明手工酸奶"};
                descriptions = new String[]{"当日巴氏杀菌鲜牛奶", "无添加手工老酸奶"};
                unit = "盒"; break;
            case "BREAD_TOAST":
                names = new String[]{"全麦吐司 500g", "北海道牛奶吐司"};
                descriptions = new String[]{"无糖高纤全麦", "香浓牛奶风味"};
                unit = "袋"; break;
            case "CAKE_DESSERT":
                names = new String[]{"提拉米苏 6 寸", "轻乳酪蛋糕"};
                descriptions = new String[]{"意式经典提拉米苏", "入口即化石纹乳酪"};
                unit = "个"; break;
            case "SNACK":
                names = new String[]{"手工曲奇 200g", "坚果能量棒 6 条"};
                descriptions = new String[]{"黄油香浓手工曲奇", "每日坚果能量棒"};
                unit = "盒"; break;
            default:
                // 50+ 真实社区商品名，足够覆盖 60 条不重名
                String[] pool = {
                    "有机小青菜 500g", "本地鸡毛菜 300g", "崇明糯玉米 4 根", "本地草莓 1 盒",
                    "山东红富士苹果 5 斤", "冰糖心丑苹果 4 斤", "阳光玫瑰葡萄 2 斤", "夏黑葡萄 1.5 斤",
                    "四川丑橘 4 斤", "赣南脐橙 5 斤", "泰国金枕榴莲 1 个", "海南贵妃芒 3 斤",
                    "苏北土鸡蛋 30 枚", "崇明散养鸭蛋 20 枚", "农家鸽子蛋 10 枚", "山东鹅蛋 6 枚",
                    "东海小黄鱼 1 斤", "舟山带鱼段 2 斤", "福建南日鲍 10 只", "广东基围虾 1 斤",
                    "崇明金瓜 1 只", "有机胡萝卜 500g", "本地西红柿 2 斤", "山东大葱 3 根",
                    "蓝月亮深层洗衣液 3kg", "威露士除菌洗衣液 2kg", "洁柔古龙水味皂 4 块", "超能皂粉 1.5kg",
                    "清风原木抽纸 24 包", "维达超韧抽纸 12 包", "心相印厨房纸 8 卷", "卷纸 20 卷家用装",
                    "金龙鱼黄金比例调和油 5L", "福临门压榨玉米油 5L", "鲁花纯花生油 5L", "西王玉米胚芽油 5L",
                    "香雪高筋面粉 5kg", "金龙鱼麦芯粉 5kg", "安佳黄油 454g", "雀巢全脂奶粉 900g",
                    "海天酿造酱油 1.9L", "厨邦特级生抽 1.2L", "恒顺香醋 1.5L", "老干妈豆鼓 280g",
                    "奥利奥原味饼干 400g", "乐事薯片礼包 10 包", "三只松鼠坚果礼盒 1.5kg", "良品铺子每日坚果 30 包",
                    "农夫山泉天然水 24 瓶", "怡宝纯净水 24 瓶", "可口可乐 330ml×24", "王老吉凉茶 16 罐",
                    "光明莫斯利安酸奶 24 盒", "伊利安慕希酸奶 16 盒", "蒙牛特仑苏纯牛奶 16 盒", "光明优+纯牛奶 24 盒",
                    "崇明大米 10 斤装", "五常稻花香米 10 斤", "苏北珍珠大米 10 斤", "泰国香米 5kg"
                };
                names = new String[]{pool[seq % pool.length]};
                descriptions = new String[]{"团长精选·产地直供", "社区团购爆款"};
                unit = "份";
        }

        // 商品（1 个）
        Product p = new Product();
        p.setProductCode("SKU-SEED-" + String.format("%03d", seq));
        p.setProductName(names[seq % 2]);
        p.setCategoryId(subCat.getId());
        p.setMerchantId(m.getId());
        p.setCommunityId(comm.getId());
        p.setUnit(unit);
        p.setStatus("ON_SHELF");
        p.setDescription(descriptions[seq % 2]);
        p.setMainImage("");
        // 约 1/3 商品设 deadlineTime 为未来 1~3 天 → comingSoon 命中
        if (seq % 3 == 0) {
            p.setDeadlineTime(LocalDateTime.now().plusDays(1 + (seq % 3)).plusHours(18));
        } else {
            p.setDeadlineTime(LocalDateTime.now().plusDays(7 + (seq % 5)).plusHours(22));
        }
        productMapper.insert(p);

        // SKU（2 个，同商品不同规格）
        ProductSku sku1 = new ProductSku();
        sku1.setProductId(p.getId());
        sku1.setSkuCode("SKU-" + p.getId() + "-A");
        sku1.setSkuName(names[seq % 2] + " 标准装");
        sku1.setSpec("标准装");
        BigDecimal price1 = BigDecimal.valueOf(8 + (seq % 20));
        sku1.setPrice(price1);
        sku1.setOriginalPrice(price1.add(BigDecimal.valueOf(3)));
        sku1.setCostPrice(price1.multiply(BigDecimal.valueOf(0.6)));
        sku1.setStatus("ON_SHELF");
        productSkuMapper.insert(sku1);

        ProductSku sku2 = new ProductSku();
        sku2.setProductId(p.getId());
        sku2.setSkuCode("SKU-" + p.getId() + "-B");
        sku2.setSkuName(names[seq % 2] + " 家庭装");
        sku2.setSpec("家庭装");
        BigDecimal price2 = price1.multiply(BigDecimal.valueOf(2.3)).setScale(2, BigDecimal.ROUND_HALF_UP);
        sku2.setPrice(price2);
        sku2.setOriginalPrice(price2.add(BigDecimal.valueOf(5)));
        sku2.setCostPrice(price2.multiply(BigDecimal.valueOf(0.6)));
        sku2.setStatus("ON_SHELF");
        productSkuMapper.insert(sku2);

        // 默认 SKU 指向 A
        p.setDefaultSkuId(sku1.getId());
        productMapper.updateById(p);

        // 特色属性（3-5 个，从 L292 预定义字典取）
        String[][] attrs = {
                {"产地", originOf(seq), null, "1", "1"},
                {"品牌", brandOf(subCat), null, "1", "1"},
                {"保质期", "7-30 天", "天", "0", "1"},
                {"特色标签", featureOf(seq), null, "1", "1"},
                {"重量", unit, null, "0", "0"}
        };
        for (int a = 0; a < attrs.length && a < 4 + (seq % 2); a++) {
            ProductAttribute pa = new ProductAttribute();
            pa.setProductId(p.getId());
            pa.setAttrKey(attrs[a][0]);
            pa.setAttrValue(attrs[a][1]);
            pa.setAttrUnit(attrs[a][2]);
            pa.setIsFilterable(Integer.parseInt(attrs[a][3]));
            pa.setIsHighlight(Integer.parseInt(attrs[a][4]));
            pa.setSortOrder(a + 1);
            productAttributeMapper.insert(pa);
        }

        // 库存：sku × community × pickup_point
        List<PickupPoint> commPoints = allPoints.stream()
                .filter(pp -> pp.getCommunityId().equals(comm.getId())).toList();
        if (commPoints.isEmpty()) {
            // 兜底：全局找一个
            PickupPoint pp = allPoints.isEmpty() ? null : allPoints.get(0);
            if (pp != null) commPoints = List.of(pp);
        }
        for (ProductSku sku : List.of(sku1, sku2)) {
            for (PickupPoint pp : commPoints) {
                Stock s = new Stock();
                s.setSkuId(sku.getId());
                s.setCommunityId(comm.getId());
                s.setPickupPointId(pp.getId());
                int total = 50 + (seq * 3) % 200;
                s.setTotalQty(total);
                s.setLockedQty(0);
                s.setAvailableQty(total);
                s.setWarnQty(10);
                s.setVersion(0);
                stockMapper.insert(s);
            }
        }
    }

    // ==================== 工厂方法 ====================

    private Community mkCommunity(String name, String code, String addr, String contact, String phone) {
        Community c = new Community();
        c.setCommunityName(name); c.setCommunityCode(code);
        c.setAddress(addr); c.setContactName(contact);
        c.setContactPhone(phone); c.setStatus("ACTIVE");
        return c;
    }

    private Merchant mkMerchant(String name, String code, String contact, String phone) {
        Merchant m = new Merchant();
        m.setMerchantName(name); m.setMerchantCode(code);
        m.setContactName(contact); m.setContactPhone(phone);
        m.setStatus("ACTIVE");
        return m;
    }

    private Category mkCategory(Long parentId, String name, String code, int order, String icon) {
        Category c = new Category();
        c.setParentId(parentId); c.setCategoryName(name); c.setCategoryCode(code);
        c.setSortOrder(order); c.setIcon(icon); c.setStatus("ACTIVE");
        return c;
    }

    private String originOf(int seq) {
        return new String[]{"崇明岛", "山东烟台", "浙江临海", "黑龙江", "内蒙古", "江苏兴化"}[seq % 6];
    }
    private String brandOf(Category c) {
        return switch (c.getCategoryCode()) {
            case "VEG_ORGANIC" -> "崇明有机";
            case "FRUIT_SEASON" -> "烟台苹果";
            case "MEAT_EGG" -> "崇明散养";
            case "STAPLE_GRAIN" -> "崇明大米";
            case "DAIRY" -> "光明乳业";
            case "BREAD_TOAST", "CAKE_DESSERT" -> "烘焙小作坊";
            default -> "团长严选";
        };
    }
    private String featureOf(int seq) {
        return new String[]{"当日直达", "无农药", "团购专供", "手工制作", "崇明特产", "冷链配送"}[seq % 6];
    
    }
    private void seedGroupBuyActivities() {
        List<ProductSku> skus = productSkuMapper.selectList(null);
        List<Community> comms = communityMapper.selectList(null);
        String[] titles = {"崇明有机青菜 5斤团", "烟台红富士苹果 10斤团", "土鸡蛋 30枚团",
                "崇明大米 10斤装团", "光明特仑苏 整箱团", "手工老面包 20个团",
                "崇明散养土鸡 2只团", "猪肉馅 5斤团", "奶油蛋糕 8寸团", "时令蔬菜组合 10斤团"};
        int[] minQtys = {10, 15, 20, 8, 24, 30, 5, 15, 5, 20};

        int target = 10;
        int existing = (int) (long) groupBuyActivityMapper.selectCount(null);
        LocalDateTime now = LocalDateTime.now();
        for (int i = existing; i < target; i++) {
            ProductSku sku = skus.get(i % skus.size());
            Community comm = comms.get(i % comms.size());
            GroupBuyActivity a = new GroupBuyActivity();
            a.setActivityNo("GB-SEED-" + (i + 1));
            a.setTitle(titles[i % titles.length]);
            a.setSkuId(sku.getId());
            a.setProductId(sku.getProductId());
            Product product = productMapper.selectById(sku.getProductId());
            a.setMerchantId(product != null ? product.getMerchantId() : null);
            a.setCommunityId(comm.getId());
            a.setGroupPrice(sku.getPrice().multiply(BigDecimal.valueOf(0.85)).setScale(2, java.math.RoundingMode.HALF_UP));
            a.setOriginalPrice(sku.getPrice());
            a.setMinGroupQty(minQtys[i % minQtys.length]);
            a.setMaxGroupQty(minQtys[i % minQtys.length] * 3);
            a.setStatus("OPEN");
            a.setCycleStart(now);
            a.setCycleEnd(now.plusDays(3));
            a.setExpectedArrive(now.plusDays(4));
            a.setAutoCutoff(1);
            groupBuyActivityMapper.insert(a);
        }
        log.info("[种子] 团购活动种子完成 -> {} 条", groupBuyActivityMapper.selectCount(null));
    }
}
