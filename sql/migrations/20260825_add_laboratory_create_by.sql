SET @ddl = (
    SELECT IF(
                   EXISTS(
                       SELECT 1
                       FROM INFORMATION_SCHEMA.COLUMNS
                       WHERE TABLE_SCHEMA = DATABASE()
                         AND TABLE_NAME = 'laboratory'
                         AND COLUMN_NAME = 'create_by'
                   ),
                   'SELECT 1',
                   'ALTER TABLE `laboratory`
                       ADD COLUMN `create_by` VARCHAR(64) NOT NULL DEFAULT ''super-admin''
                       COMMENT ''实验室创建用户ID''
                       AFTER `manager`'
           )
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;