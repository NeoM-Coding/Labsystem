SET @schema_name = DATABASE();

SET @add_start_section = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'timetable'
          AND column_name = 'start_section'
    ),
    'DO 0',
    'ALTER TABLE `timetable` ADD COLUMN `start_section` TINYINT NULL COMMENT ''课表展示开始节次'' AFTER `end_week`'
);
PREPARE timetable_migration_statement FROM @add_start_section;
EXECUTE timetable_migration_statement;
DEALLOCATE PREPARE timetable_migration_statement;

SET @add_end_section = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = @schema_name
          AND table_name = 'timetable'
          AND column_name = 'end_section'
    ),
    'DO 0',
    'ALTER TABLE `timetable` ADD COLUMN `end_section` TINYINT NULL COMMENT ''课表展示结束节次'' AFTER `start_section`'
);
PREPARE timetable_migration_statement FROM @add_end_section;
EXECUTE timetable_migration_statement;
DEALLOCATE PREPARE timetable_migration_statement;

UPDATE `timetable`
SET
    `start_section` = CASE
        WHEN `start_time` < '08:45:00' THEN 1
        WHEN `start_time` < '09:40:00' THEN 2
        WHEN `start_time` < '10:45:00' THEN 3
        WHEN `start_time` < '11:40:00' THEN 4
        WHEN `start_time` < '14:55:00' THEN 5
        WHEN `start_time` < '15:50:00' THEN 6
        WHEN `start_time` < '16:45:00' THEN 7
        WHEN `start_time` < '17:40:00' THEN 8
        WHEN `start_time` < '19:25:00' THEN 9
        WHEN `start_time` < '20:15:00' THEN 10
        ELSE 11
    END,
    `end_section` = CASE
        WHEN `end_time` > '20:20:00' THEN 11
        WHEN `end_time` > '19:30:00' THEN 10
        WHEN `end_time` > '18:40:00' THEN 9
        WHEN `end_time` > '16:55:00' THEN 8
        WHEN `end_time` > '16:00:00' THEN 7
        WHEN `end_time` > '15:05:00' THEN 6
        WHEN `end_time` > '14:10:00' THEN 5
        WHEN `end_time` > '10:55:00' THEN 4
        WHEN `end_time` > '10:00:00' THEN 3
        WHEN `end_time` > '08:55:00' THEN 2
        ELSE 1
    END
WHERE `start_section` IS NULL OR `end_section` IS NULL;

UPDATE `timetable`
SET `end_section` = `start_section`
WHERE `end_section` < `start_section`;

ALTER TABLE `timetable`
    MODIFY COLUMN `start_section` TINYINT NOT NULL COMMENT '课表展示开始节次',
    MODIFY COLUMN `end_section` TINYINT NOT NULL COMMENT '课表展示结束节次';

SET @add_section_constraint = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_schema = @schema_name
          AND table_name = 'timetable'
          AND constraint_name = 'chk_timetable_sections'
    ),
    'DO 0',
    'ALTER TABLE `timetable` ADD CONSTRAINT `chk_timetable_sections` CHECK (`start_section` BETWEEN 1 AND 11 AND `end_section` BETWEEN `start_section` AND 11)'
);
PREPARE timetable_migration_statement FROM @add_section_constraint;
EXECUTE timetable_migration_statement;
DEALLOCATE PREPARE timetable_migration_statement;
