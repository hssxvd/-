-- V5: 扩展自提点核销员角色（PICKUP_STAFF）
-- 业务模型 gap：原项目把自提点联系人当 pickup_point 表字段（contact_name/phone），
-- 没有系统账号。本迁移为自提点独立出一个核销员角色，登录后只能看自己自提点的订单。

-- 1) pickup_point 表加 staff_user_id（绑定核销员账号）
ALTER TABLE `pickup_point`
  ADD COLUMN `staff_user_id` BIGINT DEFAULT NULL COMMENT '自提点核销员用户 ID（users 表）'
  AFTER `community_id`;

-- 2) 新增角色：自提点核销员
INSERT INTO `role` (`id`, `role_code`, `role_name`, `description`) VALUES
(5, 'ROLE_PICKUP_STAFF', '自提点核销员', '收货入库、消费者自提核销，只能操作所属自提点');

-- 3) 新增权限（核销员需要的）
INSERT INTO `permission` (`permission_code`, `permission_name`, `module`) VALUES
('fulfillment:verify', '核销自提', 'fulfillment');

-- 4) 创建 2 个核销员账号（密码同 123456，复用 V2 已有加密串）
INSERT INTO `users` (`username`, `password`, `nickname`, `phone`, `user_type`, `community_id`, `status`) VALUES
('pickup01', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '朝阳东门老王', '13900001001', 'PICKUP_STAFF', 1, 'ACTIVE'),
('pickup02', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '朝阳西门小李', '13900001002', 'PICKUP_STAFF', 1, 'ACTIVE');

-- 5) 绑定角色
INSERT INTO `user_role` (`user_id`, `role_id`) VALUES
(7, 5), (8, 5);

-- 6) 给核销员分配权限（最少权限集，只能核销 + 看自提点）
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES
-- product:view（看商品快照）
(5, 1),
-- pickup:view（看自提点信息）
(5, 24),
-- fulfillment:view + fulfillment:verify（看履约 + 核销自提）
(5, 80), (5, 84),
-- order:view（看订单）
(5, 60);

-- 7) 绑定自提点
UPDATE `pickup_point` SET `staff_user_id` = 7 WHERE `id` = 1;  -- 朝阳花园东门 → pickup01
UPDATE `pickup_point` SET `staff_user_id` = 8 WHERE `id` = 2;  -- 朝阳花园西门 → pickup02
