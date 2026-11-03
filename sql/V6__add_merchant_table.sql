-- V6: 新增 merchant 商家主表（约束文档 3.1 多商家加盟模型正式落地）
--
-- 【问题】此前项目无 merchant 表，所有 Service 把 SecurityContext.getUserId() 当 merchantId 用，
--         依赖巧合 user.id == product.merchant_id。新增第三个商家即崩。
-- 【修复】建 merchant 表 + 回填数据 + user 表绑定 merchant_id。
-- 【影响】所有 Service 从 SecurityContext.getUserId() 改为 SecurityContext.getMerchantId()。

CREATE TABLE IF NOT EXISTS merchant (
  id BIGINT NOT NULL AUTO_INCREMENT,
  merchant_name VARCHAR(100) NOT NULL,
  merchant_code VARCHAR(50) NOT NULL,
  contact_name VARCHAR(50) DEFAULT NULL,
  contact_phone VARCHAR(20) DEFAULT NULL,
  community_id BIGINT DEFAULT NULL,
  business_scope VARCHAR(500) DEFAULT '[]',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  remark VARCHAR(500) DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_merchant_code (merchant_code),
  KEY idx_community_id (community_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 回填商家数据（从 V3 seed 推断）
INSERT INTO merchant (id, merchant_name, merchant_code, contact_name, contact_phone, community_id, status) VALUES
(3, '老张蔬果', 'M0003', '老张', '13900000001', 1, 'ACTIVE'),
(4, '李记日用', 'M0004', '李老板', '13900000002', 1, 'ACTIVE');

-- user 表绑定 merchant_id（之前全是 NULL，靠 user.id == product.merchant_id 巧合跑通）
UPDATE user SET merchant_id = 3 WHERE username = 'merchant01';
UPDATE user SET merchant_id = 4 WHERE username = 'merchant02';
