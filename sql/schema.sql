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
    CONSTRAINT `fk_device_gateway`
        FOREIGN KEY (`gateway_id`) REFERENCES `gateway` (`id`)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT `fk_device_socket_gateway`
        FOREIGN KEY (`socket_gateway_id`) REFERENCES `gateway` (`id`)
        ON UPDATE CASCADE ON DELETE RESTRICT,
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
