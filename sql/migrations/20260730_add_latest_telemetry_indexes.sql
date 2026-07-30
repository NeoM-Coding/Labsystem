SET @schema_name = DATABASE();

SET @add_latest_index = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = @schema_name
          AND table_name = 'access_record'
          AND index_name = 'idx_access_record_latest'
    ),
    'DO 0',
    'ALTER TABLE `access_record` ADD INDEX `idx_access_record_latest` (`device_id`, `delete_at`, `create_at` DESC, `id` DESC)'
);
PREPARE telemetry_index_statement FROM @add_latest_index;
EXECUTE telemetry_index_statement;
DEALLOCATE PREPARE telemetry_index_statement;

SET @add_latest_index = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = @schema_name
          AND table_name = 'air_condition_record'
          AND index_name = 'idx_air_condition_record_latest'
    ),
    'DO 0',
    'ALTER TABLE `air_condition_record` ADD INDEX `idx_air_condition_record_latest` (`device_id`, `delete_at`, `create_at` DESC, `id` DESC)'
);
PREPARE telemetry_index_statement FROM @add_latest_index;
EXECUTE telemetry_index_statement;
DEALLOCATE PREPARE telemetry_index_statement;

SET @add_latest_index = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = @schema_name
          AND table_name = 'circuit_break_record'
          AND index_name = 'idx_circuit_break_record_latest'
    ),
    'DO 0',
    'ALTER TABLE `circuit_break_record` ADD INDEX `idx_circuit_break_record_latest` (`device_id`, `delete_at`, `create_at` DESC, `id` DESC)'
);
PREPARE telemetry_index_statement FROM @add_latest_index;
EXECUTE telemetry_index_statement;
DEALLOCATE PREPARE telemetry_index_statement;

SET @add_latest_index = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = @schema_name
          AND table_name = 'light_record'
          AND index_name = 'idx_light_record_latest'
    ),
    'DO 0',
    'ALTER TABLE `light_record` ADD INDEX `idx_light_record_latest` (`device_id`, `delete_at`, `create_at` DESC, `id` DESC)'
);
PREPARE telemetry_index_statement FROM @add_latest_index;
EXECUTE telemetry_index_statement;
DEALLOCATE PREPARE telemetry_index_statement;

SET @add_latest_index = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = @schema_name
          AND table_name = 'sensor_record'
          AND index_name = 'idx_sensor_record_latest'
    ),
    'DO 0',
    'ALTER TABLE `sensor_record` ADD INDEX `idx_sensor_record_latest` (`device_id`, `delete_at`, `create_at` DESC, `id` DESC)'
);
PREPARE telemetry_index_statement FROM @add_latest_index;
EXECUTE telemetry_index_statement;
DEALLOCATE PREPARE telemetry_index_statement;
