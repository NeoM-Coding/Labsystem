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
    CONSTRAINT `chk_timetable_time` CHECK (`start_time` < `end_time`),
    CONSTRAINT `chk_timetable_weekday` CHECK (`weekday` BETWEEN 1 AND 7)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课表表';
