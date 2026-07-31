-- 系统用户与联系人共用 user 表：
-- 系统用户同时拥有 username/password，联系人两者均为空。
-- 本迁移由 deploy.sh 在 schema.sql 之后重复执行，因此所有操作必须幂等。

SET @schema_name = DATABASE();

SET @drop_username_check = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_schema = @schema_name
          AND table_name = 'user'
          AND constraint_name = 'chk_user_username_not_blank'
          AND constraint_type = 'CHECK'
    ),
    'ALTER TABLE `user` DROP CHECK `chk_user_username_not_blank`',
    'DO 0'
);
PREPARE user_migration_statement FROM @drop_username_check;
EXECUTE user_migration_statement;
DEALLOCATE PREPARE user_migration_statement;

SET @drop_password_check = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_schema = @schema_name
          AND table_name = 'user'
          AND constraint_name = 'chk_user_password_not_blank'
          AND constraint_type = 'CHECK'
    ),
    'ALTER TABLE `user` DROP CHECK `chk_user_password_not_blank`',
    'DO 0'
);
PREPARE user_migration_statement FROM @drop_password_check;
EXECUTE user_migration_statement;
DEALLOCATE PREPARE user_migration_statement;

SET @drop_credentials_check = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_schema = @schema_name
          AND table_name = 'user'
          AND constraint_name = 'chk_user_credentials_complete'
          AND constraint_type = 'CHECK'
    ),
    'ALTER TABLE `user` DROP CHECK `chk_user_credentials_complete`',
    'DO 0'
);
PREPARE user_migration_statement FROM @drop_credentials_check;
EXECUTE user_migration_statement;
DEALLOCATE PREPARE user_migration_statement;

ALTER TABLE `user`
    MODIFY COLUMN `name` VARCHAR(128) NOT NULL COMMENT '用户或联系人姓名，系统内唯一',
    MODIFY COLUMN `username` VARCHAR(128) NULL COMMENT '系统用户登录名，联系人为空',
    MODIFY COLUMN `password` VARCHAR(255) NULL COMMENT '系统用户密码摘要，联系人为空';

-- 兼容早期版本用空字符串表达“联系人无登录凭据”的数据。
UPDATE `user`
SET `username` = NULL,
    `password` = NULL
WHERE (`username` IS NULL OR CHAR_LENGTH(TRIM(`username`)) = 0)
  AND (`password` IS NULL OR CHAR_LENGTH(TRIM(`password`)) = 0);

SET @add_name_unique = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = @schema_name
          AND table_name = 'user'
          AND index_name = 'uk_user_name'
          AND non_unique = 0
    ),
    'DO 0',
    'ALTER TABLE `user` ADD UNIQUE KEY `uk_user_name` (`name`)'
);
PREPARE user_migration_statement FROM @add_name_unique;
EXECUTE user_migration_statement;
DEALLOCATE PREPARE user_migration_statement;

SET @add_username_unique = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = @schema_name
          AND table_name = 'user'
          AND index_name = 'uk_user_username'
          AND non_unique = 0
    ),
    'DO 0',
    'ALTER TABLE `user` ADD UNIQUE KEY `uk_user_username` (`username`)'
);
PREPARE user_migration_statement FROM @add_username_unique;
EXECUTE user_migration_statement;
DEALLOCATE PREPARE user_migration_statement;

ALTER TABLE `user`
    ADD CONSTRAINT `chk_user_username_not_blank`
        CHECK (`username` IS NULL OR CHAR_LENGTH(TRIM(`username`)) > 0),
    ADD CONSTRAINT `chk_user_password_not_blank`
        CHECK (`password` IS NULL OR CHAR_LENGTH(TRIM(`password`)) > 0),
    ADD CONSTRAINT `chk_user_credentials_complete`
        CHECK (
            (`username` IS NULL AND `password` IS NULL)
            OR (`username` IS NOT NULL AND `password` IS NOT NULL)
        );
