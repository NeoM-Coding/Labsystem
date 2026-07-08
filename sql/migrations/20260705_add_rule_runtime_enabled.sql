-- 仅用于已经创建过旧版 rule_runtime 表的数据库。
ALTER TABLE `rule_runtime`
    ADD COLUMN `enabled` TINYINT(1) NOT NULL DEFAULT 1
        COMMENT '当前发布Revision是否启用' AFTER `owner_id`,
    ADD KEY `idx_rule_runtime_enabled` (`enabled`, `delete_at`),
    ADD CONSTRAINT `chk_rule_runtime_enabled`
        CHECK (`enabled` IN (0, 1));
