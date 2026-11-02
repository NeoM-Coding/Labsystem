-- BI materialization, durable jobs and verified archive metadata. MySQL 8.
CREATE TABLE IF NOT EXISTS bi_device_hourly (
 device_id VARCHAR(64) NOT NULL, laboratory_id VARCHAR(64) NULL,
 kind VARCHAR(32) NOT NULL, hour_start DATETIME(3) NOT NULL, payload JSON NOT NULL,
 PRIMARY KEY(device_id,kind,hour_start), KEY idx_bi_hour_scope(kind,hour_start,laboratory_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS bi_summary_day (
 summary_date DATE PRIMARY KEY, sealed TINYINT(1) NOT NULL DEFAULT 0, calculated_at DATETIME(3) NOT NULL
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS bi_job_lease (
 job_name VARCHAR(64) PRIMARY KEY, owner VARCHAR(64) NOT NULL, expires_at DATETIME(3) NOT NULL
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS bi_archive_anchor (
 table_name VARCHAR(64) NOT NULL, device_id VARCHAR(64) NOT NULL,
 observed_at DATETIME(3) NOT NULL, record_id VARCHAR(64) NOT NULL, payload JSON NOT NULL,
 PRIMARY KEY(table_name,device_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS bi_archive_batch (
 id VARCHAR(64) PRIMARY KEY, table_name VARCHAR(64) NOT NULL, archive_date DATE NOT NULL,
 object_key VARCHAR(512) NOT NULL, row_count INT NOT NULL, deleted_count INT NOT NULL DEFAULT 0,
 status VARCHAR(32) NOT NULL, sha256 CHAR(64) NULL, manifest_sha256 CHAR(64) NULL,
 error_message VARCHAR(2000) NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 finished_at DATETIME(3) NULL, KEY idx_bi_archive_status(status,created_at)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS bi_work_request (
 id VARCHAR(64) PRIMARY KEY, kind VARCHAR(32) NOT NULL, start_date DATE NULL, end_date DATE NULL,
 next_date DATE NULL, dry_run TINYINT(1) NOT NULL DEFAULT 1, status VARCHAR(32) NOT NULL,
 requested_by VARCHAR(64) NOT NULL, result_message VARCHAR(2000) NULL, error_message VARCHAR(2000) NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), started_at DATETIME(3) NULL,
 finished_at DATETIME(3) NULL, KEY idx_bi_work_status(status,created_at)
) ENGINE=InnoDB;

-- Repeatable for both fresh schemas and existing databases.
SET @schema_name = DATABASE();

SET @add_archive_index = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = @schema_name AND table_name = 'access_record' AND index_name = 'idx_access_record_archive_time'
    ),
    'DO 0',
    'ALTER TABLE access_record ADD INDEX idx_access_record_archive_time(create_at,id)'
);
PREPARE archive_index_statement FROM @add_archive_index;
EXECUTE archive_index_statement;
DEALLOCATE PREPARE archive_index_statement;

SET @add_archive_index = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = @schema_name AND table_name = 'air_condition_record' AND index_name = 'idx_air_condition_record_archive_time'
    ),
    'DO 0',
    'ALTER TABLE air_condition_record ADD INDEX idx_air_condition_record_archive_time(create_at,id)'
);
PREPARE archive_index_statement FROM @add_archive_index;
EXECUTE archive_index_statement;
DEALLOCATE PREPARE archive_index_statement;

SET @add_archive_index = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = @schema_name AND table_name = 'circuit_break_record' AND index_name = 'idx_circuit_break_record_archive_time'
    ),
    'DO 0',
    'ALTER TABLE circuit_break_record ADD INDEX idx_circuit_break_record_archive_time(create_at,id)'
);
PREPARE archive_index_statement FROM @add_archive_index;
EXECUTE archive_index_statement;
DEALLOCATE PREPARE archive_index_statement;

SET @add_archive_index = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = @schema_name AND table_name = 'light_record' AND index_name = 'idx_light_record_archive_time'
    ),
    'DO 0',
    'ALTER TABLE light_record ADD INDEX idx_light_record_archive_time(create_at,id)'
);
PREPARE archive_index_statement FROM @add_archive_index;
EXECUTE archive_index_statement;
DEALLOCATE PREPARE archive_index_statement;

SET @add_archive_index = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = @schema_name AND table_name = 'sensor_record' AND index_name = 'idx_sensor_record_archive_time'
    ),
    'DO 0',
    'ALTER TABLE sensor_record ADD INDEX idx_sensor_record_archive_time(create_at,id)'
);
PREPARE archive_index_statement FROM @add_archive_index;
EXECUTE archive_index_statement;
DEALLOCATE PREPARE archive_index_statement;

CREATE TABLE IF NOT EXISTS bi_projection_cursor (
 device_id VARCHAR(64) PRIMARY KEY, record_id VARCHAR(64) NOT NULL, observed_at DATETIME(3) NOT NULL
) ENGINE=InnoDB;
