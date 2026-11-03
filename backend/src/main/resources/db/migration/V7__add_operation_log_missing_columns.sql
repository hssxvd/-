-- V7 补 operation_log 缺失列（BaseEntity.update_time / is_deleted）
-- 之前 V1 建表时漏了，导致所有 @OpLog 标注的 Service 写日志时 WARN Unknown column 'update_time'

ALTER TABLE operation_log
    ADD COLUMN update_time DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER create_time,
    ADD COLUMN is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除' AFTER update_time;