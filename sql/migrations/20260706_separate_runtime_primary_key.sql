-- 将 Runtime 业务标识与 BaseEntity 分布式主键分离。
-- 旧数据的 id 暂时保留原值；新数据由 MyBatis-Plus ASSIGN_ID 自动生成。
ALTER TABLE `rule_runtime`
    ADD COLUMN `runtime_id` VARCHAR(64) NULL
        COMMENT 'Runtime业务ID' AFTER `id`;

UPDATE `rule_runtime`
SET `runtime_id` = `id`
WHERE `runtime_id` IS NULL;

ALTER TABLE `rule_runtime_revision`
    DROP FOREIGN KEY `fk_rule_runtime_revision_runtime`;

ALTER TABLE `rule_runtime`
    MODIFY COLUMN `runtime_id` VARCHAR(64) NOT NULL COMMENT 'Runtime业务ID',
    ADD UNIQUE KEY `uk_rule_runtime_runtime_id` (`runtime_id`);

ALTER TABLE `rule_runtime_revision`
    ADD CONSTRAINT `fk_rule_runtime_revision_runtime`
        FOREIGN KEY (`runtime_id`) REFERENCES `rule_runtime` (`runtime_id`)
        ON UPDATE CASCADE ON DELETE RESTRICT;
