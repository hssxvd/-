# 社区团购电商平台 · 全模块流程文档

> **版本**：v1.0 · 2026-10-14  
> **架构**：Spring Boot 2.x + MyBatis-Plus + Redis + MySQL · Vue 3 + Element Plus  
> **数据库**：MySQL 8.x · 23 张业务表  
> **角色**：消费者 · 商家 · 核销员（自提点） · 管理员

---

## 目录

1. [系统架构](#一系统架构)
2. [认证授权](#二认证授权)
3. [商品中心](#三商品中心)
4. [社区与自提点](#四社区与自提点)
5. [库存与供应](#五库存与供应)
6. [购物车与下单](#六购物车与下单)
7. [支付](#七支付)
8. [订单管理](#八订单管理)
9. [履约与核销](#九履约与核销)
10. [收藏功能](#十收藏功能)
11. [评价](#十一评价)
12. [售后](#十二售后)
13. [经营分析](#十三经营分析)
14. [管理员后台](#十四管理员后台)
15. [定时任务](#十五定时任务)
16. [高并发设计](#十六高并发设计)

---

## 一、系统架构

### 1.1 技术栈

| 层 | 组件 | 版本 |
|---|---|---|
| 后端框架 | Spring Boot | 2.x |
| ORM | MyBatis-Plus | 3.x |
| 数据库 | MySQL | 8.x · utf8mb4 |
| 缓存 | Redis | 6+ |
| 连接池 | Druid | 1.2.x |
| 数据库迁移 | Flyway | 9.x |
| JWT | jjwt | 0.11.x |
| 密码加密 | Spring Security Crypto | — |
| API 文档 | Knife4j（仅 dev） | 4.x |
| 前端框架 | Vue 3 | 3.4+ |
| 状态管理 | Pinia | 2.1+ |
| UI | Element Plus | 2.7+ |
| HTTP | Axios | 1.6+ |
| 图表 | ECharts | 5.5+ |
| 构建 | Vite | 5.2+ |

### 1.2 项目结构

```
community-group-buy/
├── backend/                          Spring Boot 后端
│   ├── src/main/java/com/project/groupbuy/
│   │   ├── common/                   通用：Context / Exception / Result / Config / Constant
│   │   ├── security/                 JwtAuthInterceptor / SecurityContext
│   │   ├── modules/                  业务模块（按领域拆分）
│   │   │   ├── auth/                 登录
│   │   │   ├── user/                 用户
│   │   │   ├── merchant/             商家
│   │   │   ├── product/              商品 / SKU / 属性
│   │   │   ├── community/            社区 / 自提点
│   │   │   ├── supply/               库存 Stock / 供应计划 SupplyPlan
│   │   │   ├── cart/                 购物车
│   │   │   ├── order/                订单
│   │   │   ├── payment/              支付
│   │   │   ├── fulfillment/          履约
│   │   │   ├── favorite/             收藏
│   │   │   ├── review/               评价
│   │   │   ├── aftersale/            售后
│   │   │   ├── analytics/            经营分析
│   │   │   ├── forecast/             销量预测
│   │   │   └── admin/                管理员后台
│   │   └── config/                   WebMvcConfig / RedisConfig / Flyway
│   └── src/main/resources/
│       ├── application.yml           主配置（默认 knife4j/druid 关）
│       ├── application-dev.yml       开发态
│       ├── application-prod.yml      生产态
│       └── db/migration/             Flyway 脚本 V1 ~ Vn
└── frontend/                         Vue 3 前端
    ├── src/
    │   ├── api/index.js              所有 Axios 请求函数
    │   ├── store/                    Pinia Store（cartStore 购物车角标）
    │   ├── layouts/                  4 个布局：Consumer / Merchant / Pickup / Admin
    │   ├── views/
    │   │   ├── Login.vue
    │   │   ├── consumer/             12 个页面
    │   │   ├── merchant/             10 个页面
    │   │   ├── pickup/               1 个页面（核销工作台）
    │   │   └── admin/                7 个页面
    │   ├── router/index.js           路由 + 守卫 + 按角色重定向
    │   └── App.vue
    └── vite.config.js                代理 /api → localhost:8080
```

### 1.3 数据库 ER（23 张表）

| 领域 | 表 | 说明 |
|---|---|---|
| **认证** | user / role / permission / user_role / role_permission | RBAC 模型 |
| **商家** | merchant | 商家入驻信息 |
| **商品** | product / product_sku / product_attribute / product_sku_attribute / category | 三级分类 + SPU/SKU |
| **地理** | community / pickup_point | 社区（小区）+ 自提点 |
| **库存** | stock / stock_record | sku × community × pickup_point 三维库存 |
| **供应** | supply_plan | 商家进货计划 → 入库 → 到达 |
| **交易** | cart_item / orders / order_item / order_status_log / payment / payment_callback_log | 购物车 → 订单 → 支付 |
| **履约** | fulfillment / fulfillment_record | 自提/配送状态机 |
| **互动** | product_favorite / review | 收藏 + 评价 |
| **售后** | aftersale / aftersale_record | 退款/退货 |
| **运营** | operation_log / forecast_task / forecast_result / forecast_metric | 操作日志 + AI 预测 |

### 1.4 四角色与路由

| 角色 | user_type | Layout | 路由前缀 | 主入口 |
|---|---|---|---|---|
| 消费者 | CONSUMER | ConsumerLayout | `/consumer/*` | `/consumer/home` |
| 商家 | MERCHANT | MerchantLayout | `/merchant/*` | `/merchant/dashboard` |
| 核销员 | PICKUP_STAFF | PickupLayout | `/pickup/*` | `/pickup/fulfillment` |
| 管理员 | ADMIN | AdminLayout | `/admin/*` | `/admin/dashboard` |

---

## 二、认证授权

### 2.1 登录流程

```
前端 Login.vue（四 Tab 选择角色）
    │ POST /auth/login { username, password }
    ▼
AuthController.login()
    │ BCryptPasswordEncoder.matches() 校验密码
    │ JwtUtil.generate(userId, userType, username)
    ▼
返回 { token, userType, user }
    │
    ▼
前端 AuthStore.setToken() → localStorage.setItem('jwt_token')
    │
    ▼
router/index.js redirectByRole()
    CONSUMER     → /consumer/home
    MERCHANT     → /merchant/dashboard
    PICKUP_STAFF → /pickup/fulfillment
    ADMIN        → /admin/dashboard
```

### 2.2 JWT 鉴权链路

```
请求进入 → Authorization: Bearer eyJhbGciOi...
    │
    ▼
JwtAuthInterceptor.preHandle()          ← 注册在 WebMvcConfig.addPathPatterns("/**")
    │ JwtUtil.parse(token) → userId / userType / username
    │ SecurityContext.set(userId, userType, username)   ← ThreadLocal
    │
    ▼
Controller 执行业务
    │
    ▼
JwtAuthInterceptor.afterCompletion()   ← 必触发（try/finally）
    │ SecurityContext.clear()           ← ThreadLocal 清理
    ▼
请求结束
```

### 2.3 权限控制

- **数据隔离**：每个 Service 方法开头 `Long userId = SecurityContext.getUserId()` 确保只能操作自己的资源
- **商家隔离**：`merchant_id = 当前商家ID` 自动注入查询
- **核销员隔离**：`pickup_point_id = 当前核销员绑定的自提点`
- **全局异常处理**：`GlobalExceptionHandler` 拦截 BusinessException（含 1001/2001 等错误码）

---

## 三、商品中心

### 3.1 数据模型

```
category (三级树: 生鲜 → 蔬菜 → 青菜)
    └── product (SPU: 有机崇明青菜)
        └── product_sku (SKU: 500g/把 · 10枚/盒 不同规格)
            ├── product_sku_attribute (规格值: 500g × 2 把)
            └── stock (sku × community × pickup_point)
```

### 3.2 流程总览

```
商家端 Products.vue
    │ 新增商品 → ProductController.shelf()
    │ 编辑商品 → ProductController.update()
    │ 上架    → POST /products/{id}/shelf
    │ 下架    → POST /products/{id}/offShelf
    │ 删除    → DELETE /products/{id}（逻辑删除 is_deleted=1）
    │
    ▼
管理员 AdminProducts.vue
    │ 审核（商家发布后需管理员审批）
    ▼
消费者 Home.vue / Category.vue
    │ GET /products?categoryId=xxx&page=1&size=10
    │ GET /products/categoryTree  商品分类树
    │ GET /products/attrDict      可筛选属性字典
    │
    ▼
ProductService.fillStockInfo(products, communityId)
    │ 批量查 stock → 按 skuId 分组
    │ communityId != null → 取最小 available（同社区多自提点取最严格）
    │ communityId == null → 取最大 available（跨社区推荐，任一有货即显示有货）
    │ 计算 warnStatus: NORMAL / WARN / EMPTY
    ▼
前端展示商品卡片（含库存预警角标）
```

### 3.3 核心 API

| Method | URL | 说明 |
|---|---|---|
| GET | `/products?categoryId=&keyword=&communityId=&page=&size=` | 商品列表（匿名可访问） |
| GET | `/products/{id}` | 商品详情 |
| GET | `/products/categoryTree` | 分类树 |
| GET | `/products/attrDict` | 可筛选属性字典 |
| POST | `/products/{id}/shelf` | 上架（商家） |
| POST | `/products/{id}/offShelf` | 下架 |
| PUT | `/products/{id}` | 更新商品 |
| DELETE | `/products/{id}` | 逻辑删除 |

---

## 四、社区与自提点

```
Community (朝阳花园小区)
    └── PickupPoint (东门便利店 · 西门便利店)
        └── Stock (每个 SKU × 自提点 一条库存)
```

### 4.1 流程

```
消费者 Checkout.vue
    │ GET /communities → 选自己的社区
    │ GET /pickup-points?communityId=1 → 显示可用自提点
    │ → 自提点详情（地址 / 联系人 / 营业时间）
    ▼
选择自提点后，后续所有库存查询 + 锁库都绑定到该 pickup_point_id
```

---

## 五、库存与供应

### 5.1 库存三维模型

```
Stock (sku_id × community_id × pickup_point_id)
├── available_qty  可售
├── locked_qty     预占（已下单待核销）
├── total_qty      总库存 = available + locked
├── warn_qty       预警阈值（NULL 表示没设置）
└── version        乐观锁字段
```

### 5.2 库存预警三态

| warn_qty | available_qty | warnStatus | 前端展示 |
|---|---|---|---|
| 非 NULL | ≤ warn_qty | **WARN** | "库存紧张"角标 |
| NULL | = 0 | **EMPTY** | "已售罄"角标 |
| — | > warn_qty | **NORMAL** | 无角标 |

### 5.3 库存操作矩阵

| 操作 | 触发时机 | SQL | Redis |
|---|---|---|---|
| **进货入库** (supplyIn) | 商家确认供应计划到达 | `total += qty, available += qty` | ✅ syncAvailable |
| **手动调整** (adjust) | 盘点后商家调数 | `total = qty, available = total - locked` | ✅ syncAvailable |
| **下单预占** (lock) | 消费者下单 | `available -= qty, locked += qty` + `WHERE available_qty >= qty AND version = ?` | ✅ tryDecr |
| **核销实扣** (deduct) | 核销员点确认 | `locked -= qty, total -= qty`（或幂等降级只扣 total） | ✅ syncAvailable |
| **取消解锁** (unlock) | 订单取消/过期 | `available += qty, locked -= qty` | ✅ rollbackDecr |

### 5.4 供应计划流程

```
商家 Supply.vue
    │ 创建供应计划 → SupplyPlanController.create()
    │ 确认 → SupplyPlanController.confirm()
    │ 到达入库 → SupplyPlanController.arrive()
    │     ↓ 触发 StockService.supplyIn()
    │     ↓ total += qty, available += qty
    │     ↓ Redis syncAvailable()
    │ 取消 → SupplyPlanController.cancel()
```

### 5.5 库存变更日志

每次 `lock / deduct / unlock / supplyIn / adjust` 都会写 **stock_record**：

```
stock_record: { stock_id, operation_type, before_qty, after_qty, change_qty, remark, operator_id, create_time }
```

---

## 六、购物车与下单

### 6.1 购物车

```
购物车 key = cart_item (user_id × sku_id × community_id 唯一)
    │
    ├── 加入购物车 → POST /cart?skuId=&quantity=&communityId=
    ├── 改数量    → PUT /cart
    ├── 删除      → DELETE /cart/{id}
    ├── 清空      → DELETE /cart
    ├── 查列表    → GET /cart
    └── 查总数    → GET /cart/total  ← cartStore.refresh() 用
```

**前端角标联动**：
```
cartStore (Pinia 全局状态)
    ├── cartCount   ← computed，后端 GET /cart → sum(quantity)
    ├── refresh()   ← 每次加购/删/改/清空后调用
    └── clear()     ← 下单成功后调用

ConsumerLayout.vue（全局壳）
    └── cartCount = computed(() => cartStore.cartCount)
        └── onMounted → refresh()     ← 页面进来拉一次
        └── watch(isLoggedIn)         ← 登录态变化时刷新
        └── watch(route.path)         ← 每次路由跳转同步
```

### 6.2 下单流程（最核心）

```
Checkout.vue（下单确认页）
    │ ✅ 已选自提点
    │ ✅ 购物车非空
    │ 点击"提交订单"
    │
    │ ① 前端: submitting=true → 按钮 loading + disabled（防重）
    ▼
OrderController.create()
    │ ② Redis SETNX "order_submit:{userId}" TTL=30s  ← 幂等锁第二层
    │   · 已存在 → 直接 BusinessException("请勿重复提交")
    │   · Redis 挂了 → 降级放行（不挡主流程）
    │
    ▼
OrderService.create()   ← @Transactional(rollbackFor = Exception.class)
    │ ③ 拆单：购物车按 merchant_id 分组 → N 个商家 → N 笔订单
    │ ④ 逐笔生成 order_no（雪花 ID）
    │ ⑤ 逐笔锁库：
    │     ┌─ StockService.lock(stockId, qty)
    │     │   ├─ Redis DECRBY 预检（1ms 响应）
    │     │   │   ├─ remaining >= 0 → 放行
    │     │   │   ├─ remaining < 0 → INCRBY 回滚 → 抛"库存不足"
    │     │   │   └─ Redis 异常 → 降级放行
    │     │   └─ MySQL 条件更新：
    │     │       UPDATE stock SET available_qty=available_qty-qty,
    │     │                     locked_qty=locked_qty+qty,
    │     │                     version=version+1
    │     │       WHERE available_qty >= qty AND version = #{version}
    │     │       · affected=0 → 抛"并发冲突"
    │     │
    │ ⑥ 写 orders + order_item（商品快照：product_name, main_image, unit_price, quantity）
    │ ⑦ 写 order_status_log（状态变更日志）
    │
    │ 任何步骤抛异常 → @Transactional 回滚 + StockCacheService.rollbackDecr()
    │
    ▼
下单成功
    │ ⑧ Redis delete("order_submit:{userId}")  ← 清幂等锁（用户可再次下单）
    │ ⑨ 前端 cartStore.clear()                  ← 清购物车
    │ ⑩ 前端跳转到 /consumer/pay/{orderNo}       ← 进入支付页
```

### 6.3 幂等锁两层防护

| 层 | 位置 | 实现 | 效果 |
|---|---|---|---|
| **前端层** | Checkout.vue | `submitting=true` + 按钮 disabled + loading 图标 | 按钮物理不可点 |
| **后端层** | OrderController.create() | Redis `SETNX order_submit:{userId} "1" EX 30` | 服务端再次校验，防绕过前端的直接 API 调用 |

### 6.4 锁库失败回滚

```
StockService.lock()
    │ Redis 预检 DECRBY = -1（不足）
    │ → INCRBY 回滚 + throw
    │ Controller catch → 事务回滚（无影响，DB 还没执行）

OrderService 事务内
    │ MySQL 条件更新 affected=0（乐观锁冲突）
    │ → throw BusinessException
    │ → @Transactional(rollbackFor) 回滚所有已执行的 INSERT/UPDATE
    │ → StockCacheService.rollbackDecr() 把 Redis 加回去
```

---

## 七、支付

### 7.1 模拟支付流程

```
Pay.vue（模拟支付页，订单号 = ORD179xxx）
    │ 显示：订单号 / 金额 / 商品列表
    │ 按钮：模拟支付成功 / 取消订单
    │
    ▼
模拟支付成功 → PaymentController.callback()
    │ PaymentService.paySuccess(orderNo)
    │   ├─ payment.status = SUCCESS
    │   ├─ order.pay_status = PAID
    │   ├─ order.order_status = PAID
    │   ├─ 写 payment_callback_log
    │   └─ 写 order_status_log
    │
    ▼
前端跳转订单详情 / consumer/orders
```

---

## 八、订单管理

### 8.1 订单状态机

```
                    ┌────────── 已支付（钱已收，商家还没干活）
                    ▼
PENDING_PAY ──→ PAID ──→ PROCESSING ──→ PENDING_PICKUP ──→ COMPLETED
   │                │                    │
   │                │                    └──→ CANCELLED（商家主动取消）
   │                └──→ CANCELLED（支付前取消）
   │
   └──→ CANCELLED（支付前取消 / 30 分钟超时）

退款支线（全额退款）：
PAID / PROCESSING / PENDING_PICKUP ──→ REFUNDING ──→ REFUNDED（终态）
COMPLETED ──→ aftersale_status（不改变 order_status）
```

### 8.2 订单查询 API

| Method | URL | 使用者 |
|---|---|---|
| GET | `/orders/mine` | 消费者（SecurityContext.getUserId） |
| GET | `/orders/merchant?status=` | 商家（自动 merchant_id 隔离） |
| GET | `/orders/{id}` | 按主键 |
| GET | `/orders/no/{orderNo}` | 按业务编号 |
| POST | `/orders/{id}/cancel?reason=` | 取消订单（消费者） |

### 8.3 订单详情聚合

```
OrderService.detail(orderNo)
    │ 查 orders
    │ 查 order_item（商品快照）
    │ 查 order_status_log（状态流转时间线）
    │ 查 fulfillment（履约进度）
    │ 查 payment（支付状态）
    │ 聚合 → 返回一个 Map 给前端
    │ 前端展示：订单商品 / 物流进度条 / 支付状态 / 自提码
```

---

## 九、履约与核销

### 9.1 履约状态机

```
商家分配自提/配送
    ▼
PENDING_ASSIGN ──→ PENDING_PICKUP ──→ PICKED_UP ──→ COMPLETED（自提）
                    │
                    └──→ DELIVERING ──→ DELIVERED ──→ COMPLETED（配送）

任何节点 ──→ EXCEPTION（异常，需人工介入）
```

### 9.2 自提核销流程

```
核销员 PickupFulfillment.vue（登录后 /pickup/fulfillment）
    │ 顶部 Tab：待核销 / 已核销 / 今日核销
    │ 列表：待核销订单（pickup_point_id = 当前核销员绑定的自提点）
    │
    │ 点击"让消费者念此码找货" → 显示自提码（8 位数字）
    │
    │ 确认核销 → FulfillmentController.confirmPickup()
    │   ├─ 校验：fulfillment.status == PENDING_PICKUP
    │   ├─ StockService.deduct(stockId, qty)
    │   │   ├─ 正常：locked -= qty, total -= qty
    │   │   └─ 幂等降级（locked=0）：只扣 total（防止"预占库存不足"）
    │   ├─ order.order_status = COMPLETED
    │   ├─ fulfillment.status = COMPLETED
    │   ├─ 写 fulfillment_record
    │   └─ 写 order_status_log
    │
    ▼
前端订单角标从"待自提" → "已完成"
```

### 9.3 核销员数据隔离

```
用户 pickup01 绑定自提点 → pickup_point_id = 1
    │
    ▼
FulfillmentController.mine()
    │ SELECT * FROM fulfillment WHERE pickup_point_id = 1
    │ ORDER BY create_time DESC
    ▼
只看到自己自提点的订单，看不到 pickup02 西门的
```

---

## 十、收藏功能

### 10.1 数据模型

```
product_favorite {
    user_id, product_id,             ← uk_user_product 唯一约束
    favorited_at, is_deleted
}
```

### 10.2 流程

```
消费者 Product.vue（商品详情页）
    │ 右上角 ❤️ 按钮（灰色未收藏 / 红色已收藏）
    │ 加载商品后 GET /favorites/status?productId=X → 查询收藏状态
    │ 点击 → POST /favorites/toggle?productId=X
    │   ├─ 未收藏 → INSERT → 返回 true（已收藏）
    │   └─ 已收藏 → UPDATE is_deleted=1（逻辑删除） → 返回 false
    │
    ▼
收藏列表 Favorites.vue
    │ GET /favorites?pageNum=1&pageSize=20
    │   返回: [{ favoriteId, favoritedAt, productId, productName, mainImage,
    │          unit, status, defaultSkuId, merchantId, merchantName }]
    │   · 商品已下架 → 仍显示（用户收藏不丢），加"已下架"标识
    │   · 商品已物理删除 → favorite 表无 FK 不会炸，商品信息展示快照
    │
    │ 移除 → DELETE /favorites/item/{favoriteId}
    │ 清空 → DELETE /favorites
    │ 加购 → 直接用 productId + defaultSkuId 调 /cart
```

### 10.3 唯一约束设计

```sql
-- uk_user_product 只包含 (user_id, product_id)，不含 is_deleted
-- 原因：逻辑删除后 is_deleted=1，再 toggle 时如果约束含 is_deleted 会冲突
-- 现在：toggle 逻辑 = 有记录且未删除 → 删；否则 → 插入/恢复
UNIQUE KEY uk_user_product (user_id, product_id)
```

---

## 十一、评价

```
消费者 OrderDetail.vue（订单 COMPLETED 后）
    │ 点击"评价" → ReviewController.create()
    │   ├─ 校验：order_item.user_id = 当前 user_id
    │   ├─ 校验：order.status == COMPLETED
    │   ├─ 校验：同一 order_item 不重复评价
    │   └─ INSERT review (sku_id, product_id, order_item_id, rating, content, images)
    │
    ▼
商品详情 Product.vue
    │ GET /reviews?skuId=X → 展示评价列表 + 平均分
    │ GET /reviews/avg?skuId=X → 平均 rating
    │
    ▼
商家 Reviews.vue
    │ GET /reviews/merchant → 我的商品评价
    │ 回复 → POST /reviews/{id}/reply
    │ 编辑回复 → PUT /reviews/{id}/reply
```

---

## 十二、售后

```
消费者 Aftersale.vue
    │ 申请 → AftersaleController.create()
    │   ├─ 校验：订单已支付 + 未取消/未全额退款
    │   ├─ aftersale_type: REFUND_ONLY / REFUND_RETURN
    │   ├─ reason + description + images
    │   └─ 状态 → PENDING
    │
    ▼
商家/管理员审核
    │ POST /aftersales/{id}/audit
    │   ├─ APPROVED → 同意退款
    │   │   ├─ PaymentService.refund() → 模拟退款
    │   │   ├─ 全额退款 → order.order_status → REFUNDING → REFUNDED
    │   │   ├─ 库存回补（已核销的）
    │   │   └─ 部分退款 → order_status 不变
    │   └─ REJECTED → 驳回
    │
    ▼
POST /aftersales/{id}/forceRefund（管理员强制退款）
```

---

## 十三、经营分析

### 13.1 数据源

| API | 聚合维度 |
|---|---|
| GET `/analytics/dashboard` | GMV / 订单量 / 活跃用户 / 库存预警数（今日/本周/本月） |
| GET `/analytics/orders/trend` | 7 天订单趋势 |
| GET `/analytics/sales/top-products` | 热销 Top 10 |
| GET `/analytics/sales/categories` | 分类销售占比 |

### 13.2 AI 销量预测（可选）

```
ForecastController
    │ POST /forecast/trigger → 触发预测任务
    │ GET  /forecast/task/{id} → 查任务状态
    │ GET  /forecast/results → 预测结果列表
    │ GET  /forecast/metrics → 预测准确率指标
    │
    ▼
forecast_task / forecast_result / forecast_metric 三张表存数据
（预测模型为 stub，可接入外部 ML 服务）
```

---

## 十四、管理员后台

### 14.1 功能矩阵

| 模块 | 页面 | 核心操作 |
|---|---|---|
| 全局概览 | AdminDashboard | GMV / 订单 / 用户 / 库存预警 / 热销榜 |
| 用户管理 | AdminUsers | CRUD / 角色分配 / 禁用 |
| 角色权限 | AdminRoles | RBAC：角色 × 权限点 |
| 商品审核 | AdminProducts | 商家发布后审核通过/驳回 |
| 全局订单 | AdminOrders | 跨商家订单查看 / 强制取消 / 强制退款 |
| 售后监控 | AdminAftersale | 全平台售后单 / 强制退款 |
| 操作日志 | AdminLogs | operation_log 全量查看（只读） |

### 14.2 管理员 API

| Method | URL | 说明 |
|---|---|---|
| GET | `/admin/dashboard` | 仪表盘聚合 |
| GET | `/admin/users?page=&size=&userType=` | 用户列表 |
| PUT | `/admin/users/{id}/role` | 分配角色 |
| GET | `/admin/orders?status=&merchantId=` | 全局订单 |
| POST | `/admin/aftersales/{id}/forceRefund` | 强制退款 |
| GET | `/admin/logs?action=&targetType=` | 操作日志 |

---

## 十五、定时任务

### 15.1 当前唯一定时任务

| 任务 | 位置 | Cron | 行为 |
|---|---|---|---|
| **超时关单** | PaymentService.releaseTimeout() | `0 */5 * * * ?`（每 5 分钟） | 扫描 PENDING_PAY + `create_time < NOW() - 30min` 的订单 |

### 15.2 超时关单完整流程

```
@Scheduled(cron = "0 */5 * * * ?")
public void releaseTimeout() {
    // 1. 查超时订单（最多 100 条分批）
    List<Orders> expired = orderMapper.selectList(
        new LambdaQueryWrapper<Orders>()
            .eq(Orders::getOrderStatus, PENDING_PAY)
            .lt(Orders::getCreateTime, now - 30min)
            .last("LIMIT 100"));

    for (Orders order : expired) {
        try {
            // 2. 事务内执行
            //    order.order_status = CANCELLED
            //    PaymentService.releaseStock(orderId)
            //      ├─ StockService.unlock(stockId, qty)   ← locked → available
            //      ├─ StockCacheService.rollbackDecr()    ← Redis 回补
            //    写 order_status_log
            //    写 payment_callback_log（标记超时）
        } catch (Exception e) {
            // 单条失败不影响其他，打日志继续
            log.error("超时关单失败 orderNo={}", order.getOrderNo(), e);
        }
    }
}
```

### 15.3 未来扩展点（待实现）

| 任务 | 说明 |
|---|---|
| 每日库存盘点告警 | 0 点扫 warn_qty 预警 + 空仓，发告警日志 |
| 月度 GMV 汇总 | 每月 1 号凌晨跑报表 |
| 用户活跃清理 | 30 天未登录 → 活跃状态降为 inactive |

---

## 十六、高并发设计

### 16.1 总览

```
┌──────────────────────────────────────────────────────┐
│  下单链路防超卖的四层屏障                               │
├──────────────────────────────────────────────────────┤
│  Layer 1  前端按钮防抖（submitting=true）              │
│  Layer 2  后端 Redis SETNX 幂等锁（order_submit:userId EX 30）│
│  Layer 3  库存 Redis 预检（DECRBY stock:a:{skuId}:{communityId}）│
│  Layer 4  MySQL 乐观锁条件更新（WHERE available>=qty AND version=v）│
│                                                      │
│  任何一层返回不足 → 立即失败，不进下一层                   │
│  Redis 异常 → 降级到纯 MySQL（不挡主流程）                │
└──────────────────────────────────────────────────────┘
```

### 16.2 Redis 库存预检

```
StockCacheService（Redis + MySQL 双写）
    ├─ warmUp()    ← 启动时 CommandLineRunner 全量同步
    │   SELECT * FROM stock WHERE is_deleted=0
    │   → 写入 Redis key = "stock:a:{stockId}"  value = available_qty
    │
    ├─ tryDecr(stockId, qty)
    │   ├─ DECRBY stock:a:{stockId} qty    ← 1ms 响应
    │   ├─ remaining >= 0 → ✅ 返回 true
    │   ├─ remaining < 0  → INCRBY 回滚 → ❌ 返回 false
    │   └─ 异常 → ✅ 降级（Redis 挂了不挡主流程）
    │
    ├─ rollbackDecr(stockId, qty)
    │   └─ INCRBY stock:a:{stockId} qty    ← 补偿
    │
    └─ syncAvailable(stockId)
        └─ 从 MySQL 重新拉 available_qty 覆盖 Redis
```

### 16.3 Profile 隔离（生产安全）

| 配置 | 主 yml（默认） | dev yml | prod yml |
|---|---|---|---|
| knife4j.enable | **false** | **true** | false |
| Druid stat-view | **false** | true | false |
| DB 密码 | `${DB_PASSWORD:123456}` 有默认 | `123456` 明文 | `${DB_PASSWORD:}` **空（必须 ENV 注入）** |
| 日志级别 | INFO | DEBUG | INFO |
| Tomcat 线程 | 200 | 200 | 300 |

### 16.4 事务安全

- **全局** `@Transactional(rollbackFor = Exception.class)` — 所有 Service 方法（60 处）全覆盖
- **唯一索引** 23 条（订单号 / 支付号 / 购物车 / 收藏 / 核销码 / 库存三维）
- **乐观锁** `Stock.version` — 所有 UPDATE SQL 必带 `WHERE version = ?`
- **幂等降级** `StockService.deduct()` 三路分支：正常 → 降级（只扣 total）→ 报错

### 16.5 性能提升预期

| 场景 | 改前 | 改后 |
|---|---|---|
| 超卖拒绝响应 | 50ms（查 DB） | **1ms**（Redis） |
| 正常锁库 RT | 1× MySQL SELECT + 1× UPDATE | 1× Redis DECR + 1× MySQL UPDATE |
| 单机下单 QPS | ~300 | **~3000**（Redis 挡 90% 空轮询） |
| 防狂点 | 仅前端防抖 | **前后端双层**（SETNX 服务端兜底） |

---

## 附录 A：测试账号

| 角色 | username | password |
|---|---|---|
| 消费者 | consumer01 / consumer02 | 123456 |
| 商家 | merchant01 / merchant02 | 123456 |
| 核销员 | pickup01 / pickup02 | 123456 |
| 管理员 | **superadmin** / admin01 | 123456 |

## 附录 B：启动命令

```bash
# 开发态
mvn spring-boot:run                        # 默认 dev profile（knife4j 开）
npm run dev                                # 前端 http://localhost:5173

# 生产态
APP_PROFILE=prod \
DB_URL=jdbc:mysql://prod-db:3306/community_group_buy \
DB_USERNAME=app \
DB_PASSWORD=<强密码> \
REDIS_HOST=prod-redis \
REDIS_PASSWORD=<强密码> \
JWT_SECRET=<32+ 字符随机串> \
java -jar community-group-buy-1.0.0.jar
```

## 附录 C：关键约束（写进代码的规则）

| 约束 | 规则 | 强制执行位置 |
|---|---|---|
| `@Transactional` | 必须配 `rollbackFor = Exception.class` | 代码审查 |
| `@Slf4j` | 所有 Controller / Service 必须加 | 代码审查 |
| 订单状态 | 禁止在代码写裸串，必须用 `OrderStatusConstant.xxx` | 代码审查 |
| 角色鉴权 | 数据隔离 = `WHERE user_id = ?` 自动注入 | Service 层 |
| 物理外键 | **全库零 FK**（逻辑删除 + 快照式存储） | DDL 层 |
| 唯一索引 | 订单号 / 支付号 / 购物车 / 收藏 / 库存三维 全覆盖 | DDL 层 |
| 生产安全 | knife4j + Druid stat-view **默认关** | yml 配置层 |
| 幂等锁 | 所有"创建型接口"必须考虑狂点 | 代码审查 |
