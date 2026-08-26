CREATE TABLE IF NOT EXISTS `compensation_task` (
    `id` VARCHAR(64) NOT NULL, `code` VARCHAR(128) NOT NULL, `handler_type` VARCHAR(128) NOT NULL,
    `cron_expression` VARCHAR(128) NOT NULL, `zone_id` VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
    `misfire_policy` VARCHAR(32) NOT NULL DEFAULT 'FIRE_ONCE', `enabled` TINYINT(1) NOT NULL DEFAULT 1,
    `payload` JSON NULL, `next_fire_at` DATETIME(3) NOT NULL, `last_scheduled_at` DATETIME(3) NULL,
    `last_started_at` DATETIME(3) NULL, `last_finished_at` DATETIME(3) NULL,
    `last_status` VARCHAR(32) NULL, `last_error` VARCHAR(2000) NULL,
    `locked_by` VARCHAR(128) NULL, `lock_until` DATETIME(3) NULL,
    `create_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `delete_at` DATETIME(3) NULL, PRIMARY KEY (`id`), UNIQUE KEY `uk_compensation_task_code` (`code`),
    KEY `idx_compensation_task_due` (`enabled`,`delete_at`,`next_fire_at`,`lock_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通用补偿任务定义与运行状态';

CREATE TABLE IF NOT EXISTS `compensation_task_log` (
    `id` VARCHAR(64) NOT NULL, `task_code` VARCHAR(128) NOT NULL,
    `scheduled_at` DATETIME(3) NOT NULL, `started_at` DATETIME(3) NOT NULL,
    `finished_at` DATETIME(3) NULL, `status` VARCHAR(32) NOT NULL,
    `affected_count` INT NOT NULL DEFAULT 0, `message` VARCHAR(2000) NULL,
    `create_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `delete_at` DATETIME(3) NULL, PRIMARY KEY (`id`),
    KEY `idx_compensation_log_task_time` (`task_code`,`scheduled_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='补偿任务执行台账';
