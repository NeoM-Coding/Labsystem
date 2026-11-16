-- Preserve deleted gateway topics while allowing active gateways to reuse them.
-- A NULL key part exempts deleted rows from uniqueness checks.
-- Both indexes are replaced in one ALTER TABLE. Fresh schemas already have them.
SET @gateway_topic_indexes_ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'gateway'
          AND index_name = 'uk_gateway_rs485_send_topic'
    ),
    'ALTER TABLE `gateway`
        DROP INDEX `uk_gateway_rs485_send_topic`,
        DROP INDEX `uk_gateway_rs485_accept_topic`,
        ADD UNIQUE KEY `uk_gateway_rs485_active_send_topic` (`send_topic`, (IF(`delete_at` IS NULL, 1, NULL))),
        ADD UNIQUE KEY `uk_gateway_rs485_active_accept_topic` (`accept_topic`, (IF(`delete_at` IS NULL, 1, NULL)))',
    'DO 0'
);
PREPARE gateway_topic_indexes_statement FROM @gateway_topic_indexes_ddl;
EXECUTE gateway_topic_indexes_statement;
DEALLOCATE PREPARE gateway_topic_indexes_statement;
