-- Lab System Cloud schema.
-- Target database: MySQL 8.x / InnoDB / utf8mb4.
-- The Java model currently uses single-table inheritance:
--   common.model.device.Device      -> device
--   common.model.gateway.Gateway    -> gateway

CREATE TABLE IF NOT EXISTS `gateway` (
    `id` VARCHAR(64) NOT NULL COMMENT '网关ID',
    `gateway_name` VARCHAR(128) NULL COMMENT '网关名称',
    `using_in` JSON NULL COMMENT '网关作用的实验室ID列表',
    `gateway_type` VARCHAR(32) NOT NULL COMMENT '网关类型: RS485, Socket',
    `send_topic` VARCHAR(255) NULL COMMENT 'RS485网关发送主题',
    `accept_topic` VARCHAR(255) NULL COMMENT 'RS485网关接收主题',
    `create_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `delete_at` DATETIME(3) NULL,
    PRIMARY KEY (`id`),
    KEY `idx_gateway_type_delete` (`gateway_type`, `delete_at`),
    KEY `idx_gateway_delete` (`delete_at`),
    UNIQUE KEY `uk_gateway_rs485_send_topic` (`send_topic`),
    UNIQUE KEY `uk_gateway_rs485_accept_topic` (`accept_topic`),
    CONSTRAINT `chk_gateway_type`
        CHECK (`gateway_type` IN ('RS485', 'Socket')),
    CONSTRAINT `chk_gateway_rs485_topics`
        CHECK (
            (`gateway_type` = 'RS485' AND `send_topic` IS NOT NULL AND `accept_topic` IS NOT NULL)
            OR (`gateway_type` <> 'RS485')
        )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='网关表';

CREATE TABLE IF NOT EXISTS `device` (
    `id` VARCHAR(64) NOT NULL COMMENT '设备ID',
    `device_name` VARCHAR(128) NULL COMMENT '设备名称',
    `belong_to` VARCHAR(64) NULL COMMENT '所属实验室/业务域ID',
    `device_type` VARCHAR(32) NOT NULL COMMENT '设备类型',
    `polling` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否开启轮询',
    `gateway_id` VARCHAR(64) NULL COMMENT 'RS485网关ID',
    `address` INT NOT NULL DEFAULT 0 COMMENT '设备地址',
    `self_id` INT NULL DEFAULT NULL COMMENT '同地址下设备编号',
    `socket_gateway_id` VARCHAR(64) NULL COMMENT 'Socket网关ID',
    `group_id` VARCHAR(64) NULL COMMENT '空调机组ID',
    `is_lock` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '设备锁定/状态',
    `create_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `delete_at` DATETIME(3) NULL,
    PRIMARY KEY (`id`),
    KEY `idx_device_delete` (`delete_at`),
    KEY `idx_device_gateway_delete` (`gateway_id`, `delete_at`),
    KEY `idx_device_gateway_polling` (`gateway_id`, `polling`, `delete_at`),
    KEY `idx_device_type_gateway` (`device_type`, `gateway_id`, `delete_at`),
    KEY `idx_device_type_address` (`device_type`, `address`, `self_id`, `delete_at`),
    KEY `idx_device_belong_to` (`belong_to`, `delete_at`),
    KEY `idx_device_socket_gateway` (`socket_gateway_id`, `delete_at`),
    KEY `idx_device_group` (`group_id`, `delete_at`),
    UNIQUE KEY `uk_device_bus_address` (`gateway_id`, `device_type`, `address`, `self_id`),
    CONSTRAINT `chk_device_type`
        CHECK (`device_type` IN ('Access', 'AirCondition', 'Sensor', 'CircuitBreak', 'Light')),
    CONSTRAINT `chk_device_address_range`
        CHECK (
            (`device_type` = 'Access' AND `address` BETWEEN 1 AND 10)
            OR (`device_type` = 'CircuitBreak' AND `address` BETWEEN 11 AND 30)
            OR (`device_type` = 'AirCondition' AND `address` BETWEEN 31 AND 40)
            OR (`device_type` = 'Light' AND `address` BETWEEN 41 AND 60)
            OR (`device_type` = 'Sensor' AND `address` BETWEEN 61 AND 80)
        ),
    CONSTRAINT `chk_device_self_id`
        CHECK (`self_id` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备表';

CREATE TABLE IF NOT EXISTS `access_record` (
    `id` VARCHAR(64) NOT NULL COMMENT '记录ID',
    `device_id` VARCHAR(64) NOT NULL COMMENT '设备ID',
    `address` INT NOT NULL COMMENT '门禁地址',
    `is_open` TINYINT(1) NOT NULL COMMENT '是否开门',
    `is_lock` TINYINT(1) NOT NULL COMMENT '是否锁定门锁开关',
    `lock_status` INT NOT NULL COMMENT '门锁锁定状态',
    `delay_time` INT NOT NULL COMMENT '延迟关门时间',
    `create_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `delete_at` DATETIME(3) NULL,
    PRIMARY KEY (`id`),
    KEY `idx_access_record_device_time` (`device_id`, `create_at`),
    KEY `idx_access_record_latest` (`device_id`, `delete_at`, `create_at` DESC, `id` DESC),
    KEY `idx_access_record_address_time` (`address`, `create_at`),
    KEY `idx_access_record_delete` (`delete_at`),
    CONSTRAINT `chk_access_record_bool`
        CHECK (`is_open` IN (0, 1) AND `is_lock` IN (0, 1)),
    CONSTRAINT `chk_access_record_value`
        CHECK (`address` >= 0 AND `lock_status` >= 0 AND `delay_time` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门禁状态记录表';

CREATE TABLE IF NOT EXISTS `air_condition_record` (
    `id` VARCHAR(64) NOT NULL COMMENT '记录ID',
    `device_id` VARCHAR(64) NOT NULL COMMENT '设备ID',
    `address` INT NOT NULL COMMENT '空调地址',
    `self_id` INT NOT NULL COMMENT '内机编号',
    `is_open` TINYINT(1) NOT NULL COMMENT '是否开启',
    `mode` VARCHAR(32) NULL COMMENT '模式',
    `temperature` INT NOT NULL COMMENT '设定温度',
    `speed` VARCHAR(32) NULL COMMENT '风速',
    `room_temperature` INT NOT NULL COMMENT '房间温度',
    `error_code` INT NOT NULL COMMENT '错误码',
    `create_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `delete_at` DATETIME(3) NULL,
    PRIMARY KEY (`id`),
    KEY `idx_air_condition_record_device_time` (`device_id`, `create_at`),
    KEY `idx_air_condition_record_latest` (`device_id`, `delete_at`, `create_at` DESC, `id` DESC),
    KEY `idx_air_condition_record_addr_time` (`address`, `self_id`, `create_at`),
    KEY `idx_air_condition_record_delete` (`delete_at`),
    CONSTRAINT `chk_air_condition_record_bool`
        CHECK (`is_open` IN (0, 1)),
    CONSTRAINT `chk_air_condition_record_mode`
        CHECK (`mode` IS NULL OR `mode` IN ('Cooling', 'Heating', 'Dehumidification', 'AirSupply')),
    CONSTRAINT `chk_air_condition_record_speed`
        CHECK (`speed` IS NULL OR `speed` IN ('Low', 'Middle', 'High', 'Auto')),
    CONSTRAINT `chk_air_condition_record_value`
        CHECK (`address` >= 0 AND `self_id` >= 0 AND `error_code` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='空调状态记录表';

CREATE TABLE IF NOT EXISTS `circuit_break_record` (
    `id` VARCHAR(64) NOT NULL COMMENT '记录ID',
    `device_id` VARCHAR(64) NOT NULL COMMENT '设备ID',
    `address` INT NOT NULL COMMENT '断路器地址',
    `is_open` TINYINT(1) NOT NULL COMMENT '是否合闸',
    `is_fix` TINYINT(1) NOT NULL COMMENT '是否维修',
    `is_lock` TINYINT(1) NOT NULL COMMENT '是否锁定',
    `voltage` DECIMAL(10,3) NOT NULL COMMENT '电压',
    `current` DECIMAL(10,3) NOT NULL COMMENT '电流',
    `power` DECIMAL(10,3) NOT NULL COMMENT '功率',
    `energy` DECIMAL(12,3) NOT NULL COMMENT '能耗',
    `leakage` DECIMAL(10,3) NOT NULL COMMENT '漏电电流',
    `temperature` DECIMAL(10,3) NOT NULL COMMENT '线温',
    `create_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `delete_at` DATETIME(3) NULL,
    PRIMARY KEY (`id`),
    KEY `idx_circuit_break_record_device_time` (`device_id`, `create_at`),
    KEY `idx_circuit_break_record_latest` (`device_id`, `delete_at`, `create_at` DESC, `id` DESC),
    KEY `idx_circuit_break_record_addr_time` (`address`, `create_at`),
    KEY `idx_circuit_break_record_delete` (`delete_at`),
    CONSTRAINT `chk_circuit_break_record_bool`
        CHECK (`is_open` IN (0, 1) AND `is_fix` IN (0, 1) AND `is_lock` IN (0, 1)),
    CONSTRAINT `chk_circuit_break_record_value`
        CHECK (
            `address` >= 0
            AND `voltage` >= 0
            AND `current` >= 0
            AND `power` >= 0
            AND `energy` >= 0
            AND `leakage` >= 0
        )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='断路器状态记录表';

CREATE TABLE IF NOT EXISTS `light_record` (
    `id` VARCHAR(64) NOT NULL COMMENT '记录ID',
    `device_id` VARCHAR(64) NOT NULL COMMENT '设备ID',
    `address` INT NOT NULL COMMENT '灯地址',
    `self_id` INT NOT NULL COMMENT '自编号',
    `is_open` TINYINT(1) NOT NULL COMMENT '是否开启',
    `is_lock` TINYINT(1) NOT NULL COMMENT '是否锁定',
    `create_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `delete_at` DATETIME(3) NULL,
    PRIMARY KEY (`id`),
    KEY `idx_light_record_device_time` (`device_id`, `create_at`),
    KEY `idx_light_record_latest` (`device_id`, `delete_at`, `create_at` DESC, `id` DESC),
    KEY `idx_light_record_addr_time` (`address`, `self_id`, `create_at`),
    KEY `idx_light_record_delete` (`delete_at`),
    CONSTRAINT `chk_light_record_bool`
        CHECK (`is_open` IN (0, 1) AND `is_lock` IN (0, 1)),
    CONSTRAINT `chk_light_record_value`
        CHECK (`address` >= 0 AND `self_id` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='灯状态记录表';

CREATE TABLE IF NOT EXISTS `sensor_record` (
    `id` VARCHAR(64) NOT NULL COMMENT '记录ID',
    `device_id` VARCHAR(64) NOT NULL COMMENT '设备ID',
    `address` INT NOT NULL COMMENT '传感器地址',
    `self_id` INT NOT NULL COMMENT '内编号',
    `temperature` DECIMAL(10,3) NOT NULL COMMENT '温度',
    `humidity` DECIMAL(10,3) NOT NULL COMMENT '湿度',
    `light` DECIMAL(12,3) NOT NULL COMMENT '光照强度',
    `smoke` INT NOT NULL COMMENT '烟雾',
    `create_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `delete_at` DATETIME(3) NULL,
    PRIMARY KEY (`id`),
    KEY `idx_sensor_record_device_time` (`device_id`, `create_at`),
    KEY `idx_sensor_record_latest` (`device_id`, `delete_at`, `create_at` DESC, `id` DESC),
    KEY `idx_sensor_record_addr_time` (`address`, `self_id`, `create_at`),
    KEY `idx_sensor_record_delete` (`delete_at`),
    CONSTRAINT `chk_sensor_record_value`
        CHECK (
            `address` >= 0
            AND `self_id` >= 0
            AND `humidity` >= 0
            AND `light` >= 0
            AND `smoke` >= 0
        )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='传感器状态记录表';

CREATE TABLE IF NOT EXISTS `rule_runtime` (
    `id` VARCHAR(64) NOT NULL COMMENT '分布式主键',
    `runtime_id` VARCHAR(64) NOT NULL COMMENT 'Runtime业务ID',
    `runtime_name` VARCHAR(128) NOT NULL COMMENT '规则名称',
    `owner_id` VARCHAR(64) NOT NULL COMMENT '配置规则的用户/租户ID',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '当前发布Revision是否启用',
    `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT'
        COMMENT 'DRAFT, PUBLISHED, DISABLED',
    `published_revision_no` INT NULL COMMENT '当前发布版本号',
    `active_from` DATETIME(3) NULL COMMENT 'Runtime生效时间，包含',
    `active_until` DATETIME(3) NULL COMMENT 'Runtime失效时间，不包含',
    `create_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `delete_at` DATETIME(3) NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rule_runtime_runtime_id` (`runtime_id`),
    KEY `idx_rule_runtime_owner_status` (`owner_id`, `status`, `enabled`, `delete_at`),
    KEY `idx_rule_runtime_lifetime` (`status`, `active_from`, `active_until`, `delete_at`),
    CONSTRAINT `chk_rule_runtime_status`
        CHECK (`status` IN ('DRAFT', 'PUBLISHED', 'DISABLED')),
    CONSTRAINT `chk_rule_runtime_enabled`
        CHECK (`enabled` IN (0, 1)),
    CONSTRAINT `chk_rule_runtime_lifetime`
        CHECK (
            `active_from` IS NULL
            OR `active_until` IS NULL
            OR `active_from` < `active_until`
        )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='规则Runtime元数据';

CREATE TABLE IF NOT EXISTS `rule_runtime_revision` (
    `id` VARCHAR(64) NOT NULL COMMENT 'Revision ID',
    `runtime_id` VARCHAR(64) NOT NULL COMMENT '所属Runtime',
    `revision_no` INT NOT NULL COMMENT 'Runtime内单调递增版本号',
    `schema_version` INT NOT NULL DEFAULT 1 COMMENT 'JSON结构版本',
    `definition` JSON NOT NULL COMMENT '条件组、动作组和引用关系的完整快照',
    `checksum` CHAR(64) NULL COMMENT '规范化JSON的SHA-256',
    `created_by` VARCHAR(64) NOT NULL COMMENT '创建版本的用户ID',
    `create_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rule_runtime_revision_no` (`runtime_id`, `revision_no`),
    KEY `idx_rule_runtime_revision_time` (`runtime_id`, `create_at`),
    CONSTRAINT `chk_rule_runtime_revision_no`
        CHECK (`revision_no` > 0),
    CONSTRAINT `chk_rule_runtime_schema_version`
        CHECK (`schema_version` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='规则Runtime不可变JSON版本';

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

CREATE TABLE IF NOT EXISTS `user` (
    `id` VARCHAR(64) NOT NULL COMMENT '用户ID',
    `name` VARCHAR(128) NOT NULL COMMENT '用户或联系人姓名，系统内唯一',
    `username` VARCHAR(128) NULL COMMENT '系统用户登录名，联系人为空',
    `password` VARCHAR(255) NULL COMMENT '系统用户密码摘要，联系人为空',
    `phone` VARCHAR(255) NULL COMMENT '手机号 AES Base64 密文',
    `email` VARCHAR(255) NULL COMMENT '邮箱',
    `mark` VARCHAR(512) NULL COMMENT '备注',
    `create_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `delete_at` DATETIME(3) NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_name` (`name`),
    UNIQUE KEY `uk_user_username` (`username`),
    UNIQUE KEY `uk_user_phone` (`phone`),
    UNIQUE KEY `uk_user_email` (`email`),
    KEY `idx_user_delete` (`delete_at`),
    KEY `idx_user_create_time` (`create_at`),
    CONSTRAINT `chk_user_name_not_blank`
        CHECK (CHAR_LENGTH(TRIM(`name`)) > 0),
    CONSTRAINT `chk_user_username_not_blank`
        CHECK (`username` IS NULL OR CHAR_LENGTH(TRIM(`username`)) > 0),
    CONSTRAINT `chk_user_password_not_blank`
        CHECK (`password` IS NULL OR CHAR_LENGTH(TRIM(`password`)) > 0),
    CONSTRAINT `chk_user_credentials_complete`
        CHECK (
            (`username` IS NULL AND `password` IS NULL)
            OR (`username` IS NOT NULL AND `password` IS NOT NULL)
        ),
    CONSTRAINT `chk_user_phone_not_blank`
        CHECK (`phone` IS NULL OR CHAR_LENGTH(TRIM(`phone`)) > 0),
    CONSTRAINT `chk_user_email_not_blank`
        CHECK (`email` IS NULL OR CHAR_LENGTH(TRIM(`email`)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统用户与联系人表';

CREATE TABLE IF NOT EXISTS `laboratory` (
    `id` VARCHAR(64) NOT NULL COMMENT '实验室ID',
    `building_name` VARCHAR(128) NULL COMMENT '所属楼栋名称，用于筛选',
    `org_name` VARCHAR(128) NULL COMMENT '所属单位名称，用于筛选',
    `laboratory_name` VARCHAR(128) NOT NULL COMMENT '实验室名称',
    `extra` JSON NULL COMMENT '实验室动态配置',
    `manager` JSON NULL COMMENT '实验室负责人列表，对应 Laboratory.manager',
    `create_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `delete_at` DATETIME(3) NULL,
    PRIMARY KEY (`id`),
    KEY `idx_laboratory_building` (`building_name`, `delete_at`),
    KEY `idx_laboratory_org` (`org_name`, `delete_at`),
    KEY `idx_laboratory_name` (`laboratory_name`, `delete_at`),
    KEY `idx_laboratory_delete` (`delete_at`),
    CONSTRAINT `chk_laboratory_name_not_blank`
        CHECK (CHAR_LENGTH(TRIM(`laboratory_name`)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实验室基础信息表';

CREATE TABLE IF NOT EXISTS `semester` (
    `id` VARCHAR(64) NOT NULL COMMENT '学期ID',
    `name` VARCHAR(128) NOT NULL COMMENT '学期名称，格式 YYYY-YYYY 第N学期',
    `start_date` DATE NOT NULL COMMENT '学期开始日期',
    `end_date` DATE NOT NULL COMMENT '学期结束日期',
    `create_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `delete_at` DATETIME(3) NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_semester_name` (`name`),
    KEY `idx_semester_dates` (`start_date`, `end_date`),
    CONSTRAINT `chk_semester_dates` CHECK (`start_date` < `end_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学期表';

CREATE TABLE IF NOT EXISTS `timetable` (
    `id` VARCHAR(64) NOT NULL COMMENT '课表ID',
    `semester_id` VARCHAR(64) NOT NULL COMMENT '学期ID，不建立外键',
    `semester_info` JSON NOT NULL COMMENT '学期信息冗余快照',
    `laboratory_id` VARCHAR(64) NOT NULL COMMENT '实验室ID，不建立外键',
    `course_name` VARCHAR(255) NOT NULL COMMENT '课程名称',
    `teacher_name` VARCHAR(128) NOT NULL COMMENT '教师名称',
    `week_type` VARCHAR(16) NOT NULL COMMENT 'Single, Double, Both',
    `start_week` INT NOT NULL COMMENT '起始周，包含',
    `end_week` INT NOT NULL COMMENT '结束周，包含',
    `start_section` TINYINT NOT NULL COMMENT '课表展示开始节次',
    `end_section` TINYINT NOT NULL COMMENT '课表展示结束节次',
    `start_time` TIME NOT NULL COMMENT '开始时间，包含',
    `end_time` TIME NOT NULL COMMENT '结束时间，不包含',
    `weekday` TINYINT NOT NULL COMMENT '星期，1为周一，7为周日',
    `create_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `delete_at` DATETIME(3) NULL,
    PRIMARY KEY (`id`),
    KEY `idx_timetable_semester_lab` (`semester_id`, `laboratory_id`, `weekday`, `start_time`),
    KEY `idx_timetable_semester_teacher` (`semester_id`, `teacher_name`, `weekday`, `start_time`),
    CONSTRAINT `chk_timetable_week_type` CHECK (`week_type` IN ('Single', 'Double', 'Both')),
    CONSTRAINT `chk_timetable_weeks` CHECK (`start_week` >= 1 AND `end_week` >= `start_week`),
    CONSTRAINT `chk_timetable_sections` CHECK (`start_section` BETWEEN 1 AND 11 AND `end_section` BETWEEN `start_section` AND 11),
    CONSTRAINT `chk_timetable_time` CHECK (`start_time` < `end_time`),
    CONSTRAINT `chk_timetable_weekday` CHECK (`weekday` BETWEEN 1 AND 7)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课表表';

CREATE TABLE IF NOT EXISTS `audit_operation_log` (
    `id` VARCHAR(64) NOT NULL COMMENT '审计日志ID',
    `subject_id` VARCHAR(64) NOT NULL COMMENT '操作用户ID',
    `subject_name` VARCHAR(128) NULL COMMENT '操作用户名',
    `subject_display_name` VARCHAR(128) NULL COMMENT '操作用户显示名称',
    `operation` VARCHAR(128) NOT NULL COMMENT '方法级操作标识',
    `actions` VARCHAR(255) NOT NULL COMMENT '谓语集合: CREATE, EDIT, DELETE',
    `object_types` VARCHAR(512) NOT NULL COMMENT '宾语资源类型集合',
    `object_ids` VARCHAR(1024) NULL COMMENT '宾语资源ID集合',
    `event_types` VARCHAR(2048) NOT NULL COMMENT '参与审计的Java事件类型集合',
    `description` TEXT NOT NULL COMMENT '面向管理员的人类可读操作描述',
    `trace_id` VARCHAR(128) NULL COMMENT '关联运维链路Trace ID',
    `request_id` VARCHAR(128) NULL COMMENT '关联请求ID',
    `occurred_at` DATETIME(3) NOT NULL COMMENT '操作发生时间',
    PRIMARY KEY (`id`),
    KEY `idx_audit_subject_time` (`subject_id`, `occurred_at`),
    KEY `idx_audit_operation_time` (`operation`, `occurred_at`),
    KEY `idx_audit_trace_id` (`trace_id`),
    KEY `idx_audit_request_id` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理员操作审计日志';

CREATE TABLE IF NOT EXISTS `system_bootstrap_metadata` (
    `meta_key` VARCHAR(128) NOT NULL COMMENT '初始化元数据键',
    `meta_value` TEXT NOT NULL COMMENT '初始化元数据值',
    `update_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`meta_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部署初始化元数据';
