-- 新增规则动作组告警日志。该脚本由 deploy.sh 重复执行，必须保持幂等。

CREATE TABLE IF NOT EXISTS `alert_log` (
    `id` VARCHAR(64) NOT NULL COMMENT '告警日志ID',
    `event_id` VARCHAR(64) NOT NULL COMMENT '规则动作组执行事件ID',
    `runtime_id` VARCHAR(64) NOT NULL COMMENT 'Runtime业务ID',
    `action_group_id` VARCHAR(128) NOT NULL COMMENT '命中的动作组ID',
    `device_condition_group_id` VARCHAR(128) NULL COMMENT '命中的设备条件组ID',
    `time_condition_group_id` VARCHAR(128) NULL COMMENT '命中的时间条件组ID',
    `matched_at` DATETIME(3) NOT NULL COMMENT '条件命中时间',
    `completed_at` DATETIME(3) NOT NULL COMMENT '动作组完成时间',
    `status` VARCHAR(32) NOT NULL COMMENT 'MATCHED, SUCCESS, FAILED, PARTIAL_FAILED, NOT_IMPLEMENTED',
    `content` TEXT NULL COMMENT 'ReportAction通知内容汇总',
    `user_ids` JSON NOT NULL COMMENT 'ReportAction接收人ID去重列表',
    `actions` JSON NOT NULL COMMENT '动作执行结果快照',
    `create_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `delete_at` DATETIME(3) NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_alert_log_event_id` (`event_id`),
    KEY `idx_alert_log_runtime_matched` (`runtime_id`, `matched_at` DESC),
    KEY `idx_alert_log_action_group_matched` (`action_group_id`, `matched_at` DESC),
    KEY `idx_alert_log_status_matched` (`status`, `matched_at` DESC),
    KEY `idx_alert_log_matched` (`matched_at` DESC),
    CONSTRAINT `chk_alert_log_status`
        CHECK (`status` IN ('MATCHED', 'SUCCESS', 'FAILED', 'PARTIAL_FAILED', 'NOT_IMPLEMENTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='规则动作组告警日志';
