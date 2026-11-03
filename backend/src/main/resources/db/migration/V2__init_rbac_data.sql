-- ============================================================
-- 社区团购电商系统 角色权限种子数据
-- 版本: V2__init_rbac_data.sql
-- 依赖: V1__init_schema.sql
-- 默认密码: 123456 (BCrypt: $2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi)
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

USE `community_group_buy`;

-- ------------------------------------------------------------
-- 社区 & 自提点 (被 user 表引用，必须先插入)
-- ------------------------------------------------------------
INSERT INTO `community` (`id`, `community_name`, `community_code`, `address`, `contact_name`, `contact_phone`) VALUES
(1, '朝阳花园社区', 'COMM001', '上海市浦东新区朝阳路 100 号', '李主任', '138****1001'),
(2, '绿地香颂社区', 'COMM002', '上海市徐汇区绿地路 88 号', '赵主任', '138****1002');

INSERT INTO `pickup_point` (`id`, `community_id`, `point_name`, `address`, `contact_name`, `open_time`) VALUES
(1, 1, '朝阳花园东门自提点', '朝阳花园东门岗亭', '老王', '08:00-20:00'),
(2, 1, '朝阳花园西门自提点', '朝阳花园西门便利店', '小李', '07:30-21:00'),
(3, 2, '绿地香颂南门自提点', '绿地香颂南门快递柜旁', '小张', '08:00-19:00');

-- ------------------------------------------------------------
-- 分类 (被 product 表引用，必须先插入)
-- ------------------------------------------------------------
INSERT INTO `category` (`id`, `parent_id`, `category_name`, `category_code`, `sort_order`) VALUES
-- 一级分类
(1,  NULL, '生鲜果蔬',   'FRESH',     1),
(2,  NULL, '日用百货',   'DAILY',     2),
(3,  NULL, '粮油副食',   'GRAIN',     3),
(4,  NULL, '休闲零食',   'SNACK',     4),
(5,  NULL, '家居生活',   'HOME',      5),
-- 二级分类
(6,  1, '叶菜类',       'LEAFY',     1),
(7,  1, '根茎类',       'ROOT',      2),
(8,  1, '水果类',       'FRUIT',     3),
(9,  1, '肉禽蛋',       'MEAT',      4),
(10, 1, '海鲜水产',     'SEAFOOD',   5),
(11, 2, '清洁用品',     'CLEAN',     1),
(12, 2, '纸品湿巾',     'PAPER',     2),
(13, 2, '洗护用品',     'CARE',      3),
(14, 3, '大米面粉',     'RICE',      1),
(15, 3, '食用油',       'OIL',       2),
(16, 3, '调味品',       'SEASON',    3),
(17, 4, '饼干糕点',     'BISCUIT',   1),
(18, 4, '饮料冲调',     'DRINK',     2),
(19, 5, '餐具厨具',     'KITCHEN',   1),
(20, 5, '收纳整理',     'STORAGE',   2);

-- ------------------------------------------------------------
-- 角色
-- ------------------------------------------------------------
INSERT INTO `role` (`id`, `role_code`, `role_name`, `description`) VALUES
(1, 'ROLE_CONSUMER', '消费者', '普通消费者，浏览选购、下单、售后'),
(2, 'ROLE_MERCHANT', '商家', '商品管理、订单处理、供应计划、经营分析'),
(3, 'ROLE_COMMUNITY_ADMIN', '社区管理员', '社区/自提点管理、商品审核'),
(4, 'ROLE_SUPER_ADMIN', '超级管理员', '全部权限，跨社区管理');

-- ------------------------------------------------------------
-- 权限 (按模块组织，格式: 模块:操作)
-- ------------------------------------------------------------
INSERT INTO `permission` (`id`, `permission_code`, `permission_name`, `module`) VALUES
-- 商品模块
(1,  'product:view',       '查看商品',     'product'),
(2,  'product:add',        '新增商品',     'product'),
(3,  'product:edit',       '编辑商品',     'product'),
(4,  'product:shelf',      '上下架商品',   'product'),
(5,  'product:delete',     '删除商品',     'product'),
(6,  'product:audit',      '审核商品',     'product'),

-- 分类模块
(10, 'category:view',      '查看分类',     'category'),
(11, 'category:add',       '新增分类',     'category'),
(12, 'category:edit',      '编辑分类',     'category'),
(13, 'category:delete',    '删除分类',     'category'),

-- 社区/自提点
(20, 'community:view',     '查看社区',     'community'),
(21, 'community:add',      '新增社区',     'community'),
(22, 'community:edit',     '编辑社区',     'community'),
(23, 'community:delete',   '删除社区',     'community'),
(24, 'pickup:view',        '查看自提点',   'community'),
(25, 'pickup:add',         '新增自提点',   'community'),
(26, 'pickup:edit',        '编辑自提点',   'community'),
(27, 'pickup:delete',      '删除自提点',   'community'),

-- 库存模块
(30, 'stock:view',         '查看库存',     'stock'),
(31, 'stock:adjust',       '调整库存',     'stock'),
(32, 'stock:inbound',      '人工入库',     'stock'),

-- 供应计划
(40, 'supply:view',        '查看供应计划', 'supply'),
(41, 'supply:create',      '创建供应计划', 'supply'),
(42, 'supply:confirm',     '确认供应计划', 'supply'),
(43, 'supply:arrive',      '标记到货',     'supply'),
(44, 'supply:cancel',      '取消供应计划', 'supply'),

-- 购物车
(50, 'cart:view',          '查看购物车',   'cart'),
(51, 'cart:add',           '加入购物车',   'cart'),
(52, 'cart:edit',          '修改购物车',   'cart'),
(53, 'cart:delete',        '删除购物车',   'cart'),

-- 订单模块
(60, 'order:view',         '查看订单',     'order'),
(61, 'order:create',       '创建订单',     'order'),
(62, 'order:cancel',       '取消订单',     'order'),
(63, 'order:confirm',      '确认完成',     'order'),
(64, 'order:pay',          '支付订单',     'order'),

-- 支付模块
(70, 'payment:view',       '查看支付',     'payment'),
(71, 'payment:mock',       '模拟支付',     'payment'),
(72, 'payment:refund',     '发起退款',     'payment'),

-- 履约模块
(80, 'fulfillment:view',   '查看履约',     'fulfillment'),
(81, 'fulfillment:assign', '分配履约',     'fulfillment'),
(82, 'fulfillment:complete','完成履约',    'fulfillment'),

-- 售后模块
(90, 'aftersale:view',     '查看售后',     'aftersale'),
(91, 'aftersale:apply',    '申请售后',     'aftersale'),
(92, 'aftersale:audit',    '审核售后',     'aftersale'),

-- 评价模块
(100, 'review:view',       '查看评价',     'review'),
(101, 'review:add',        '发表评价',     'review'),
(102, 'review:reply',      '回复评价',     'review'),

-- 预测模块
(110, 'forecast:view',     '查看预测',     'forecast'),
(111, 'forecast:trigger',  '触发预测',     'forecast'),

-- 经营分析
(120, 'analysis:view',     '查看经营分析', 'analysis'),

-- 系统管理
(130, 'user:view',         '查看用户',     'system'),
(131, 'user:add',          '新增用户',     'system'),
(132, 'user:edit',         '编辑用户',     'system'),
(133, 'user:delete',       '删除用户',     'system'),
(134, 'role:view',         '查看角色',     'system'),
(135, 'role:edit',         '编辑角色权限', 'system'),
(136, 'log:view',          '查看日志',     'system');

-- ------------------------------------------------------------
-- 角色-权限关联
-- ------------------------------------------------------------
-- 消费者: 浏览+购物车+下单+支付+履约+售后申请+评价+个人预测查看
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES
-- 浏览类
(1, 1), (1, 10), (1, 20), (1, 24), (1, 30),
-- 购物车
(1, 50), (1, 51), (1, 52), (1, 53),
-- 订单
(1, 60), (1, 61), (1, 62), (1, 63),
-- 支付
(1, 70), (1, 71),
-- 履约
(1, 80),
-- 售后
(1, 90), (1, 91),
-- 评价
(1, 100), (1, 101);

-- 商家: 商品CRUD+库存+供应计划+订单处理+履约+售后审核+评价回复+经营分析+预测
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES
-- 商品
(2, 1), (2, 2), (2, 3), (2, 4), (2, 5),
-- 分类(只读)
(2, 10),
-- 社区/自提点(只读)
(2, 20), (2, 24),
-- 库存
(2, 30), (2, 31),
-- 供应计划
(2, 40), (2, 41), (2, 42), (2, 43), (2, 44),
-- 订单
(2, 60), (2, 63),
-- 支付
(2, 70),
-- 履约
(2, 80), (2, 81), (2, 82),
-- 售后
(2, 90), (2, 92),
-- 评价
(2, 100), (2, 102),
-- 预测
(2, 110),
-- 经营分析
(2, 120);

-- 社区管理员: 社区/自提点管理+分类管理+商品审核+跨商家经营分析
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES
-- 商品审核
(3, 1), (3, 6),
-- 分类
(3, 10), (3, 11), (3, 12),
-- 社区/自提点
(3, 20), (3, 21), (3, 22), (3, 24), (3, 25), (3, 26),
-- 库存(只读)
(3, 30),
-- 订单(只读)
(3, 60),
-- 售后
(3, 90), (3, 92),
-- 评价
(3, 100),
-- 预测
(3, 110),
-- 经营分析
(3, 120),
-- 日志
(3, 136);

-- 超级管理员: 全部权限
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 4, id FROM `permission`;

-- ------------------------------------------------------------
-- 演示用户 (默认密码 123456)
-- ------------------------------------------------------------
INSERT INTO `user` (`id`, `username`, `password`, `nickname`, `phone`, `user_type`, `community_id`, `status`) VALUES
(1, 'consumer01', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '小明',   '13800000001', 'CONSUMER', 1, 'ACTIVE'),
(2, 'consumer02', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '小红',   '13800000002', 'CONSUMER', 1, 'ACTIVE'),
(3, 'merchant01', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '老张蔬果', '13900000001', 'MERCHANT', 1, 'ACTIVE'),
(4, 'merchant02', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '李记日用', '13900000002', 'MERCHANT', 1, 'ACTIVE'),
(5, 'admin01',    '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '王管',   '13700000001', 'ADMIN',    1, 'ACTIVE'),
(6, 'superadmin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '超级管理员', '13600000001', 'ADMIN',  NULL, 'ACTIVE');

-- ------------------------------------------------------------
-- 演示用户-角色关联
-- ------------------------------------------------------------
INSERT INTO `user_role` (`user_id`, `role_id`) VALUES
(1, 1),   -- consumer01 -> 消费者
(2, 1),   -- consumer02 -> 消费者
(3, 2),   -- merchant01 -> 商家
(4, 2),   -- merchant02 -> 商家
(5, 3),   -- admin01 -> 社区管理员
(6, 4);   -- superadmin -> 超级管理员

SET FOREIGN_KEY_CHECKS = 1;
