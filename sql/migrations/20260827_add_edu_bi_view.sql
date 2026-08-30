CREATE TABLE IF NOT EXISTS `bi_week_number` (
    `week_number` TINYINT NOT NULL COMMENT '用于展开课表的周次维度',
    PRIMARY KEY (`week_number`),
    CONSTRAINT `chk_bi_week_number` CHECK (`week_number` BETWEEN 1 AND 60)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='BI周次维度';

INSERT IGNORE INTO `bi_week_number` (`week_number`) VALUES
    (1),(2),(3),(4),(5),(6),(7),(8),(9),(10),
    (11),(12),(13),(14),(15),(16),(17),(18),(19),(20),
    (21),(22),(23),(24),(25),(26),(27),(28),(29),(30),
    (31),(32),(33),(34),(35),(36),(37),(38),(39),(40),
    (41),(42),(43),(44),(45),(46),(47),(48),(49),(50),
    (51),(52),(53),(54),(55),(56),(57),(58),(59),(60);

CREATE OR REPLACE VIEW `bi_edu_course_occurrence` AS
SELECT
    CONCAT(
        t.id,
        ':',
        DATE_FORMAT(
            DATE_ADD(
                DATE_SUB(s.start_date, INTERVAL WEEKDAY(s.start_date) DAY),
                INTERVAL ((w.week_number - 1) * 7 + t.weekday - 1) DAY
            ),
            '%Y-%m-%d'
        )
    ) AS `id`,
    t.id AS `timetable_id`,
    t.semester_id AS `semester_id`,
    t.laboratory_id AS `laboratory_id`,
    t.course_name AS `course_name`,
    t.teacher_name AS `teacher_name`,
    w.week_number AS `week_number`,
    t.weekday AS `weekday`,
    t.start_section AS `start_section`,
    t.end_section AS `end_section`,
    DATE_ADD(
        DATE_SUB(s.start_date, INTERVAL WEEKDAY(s.start_date) DAY),
        INTERVAL ((w.week_number - 1) * 7 + t.weekday - 1) DAY
    ) AS `course_date`,
    TIMESTAMP(
        DATE_ADD(
            DATE_SUB(s.start_date, INTERVAL WEEKDAY(s.start_date) DAY),
            INTERVAL ((w.week_number - 1) * 7 + t.weekday - 1) DAY
        ),
        t.start_time
    ) AS `start_at`,
    TIMESTAMP(
        DATE_ADD(
            DATE_SUB(s.start_date, INTERVAL WEEKDAY(s.start_date) DAY),
            INTERVAL ((w.week_number - 1) * 7 + t.weekday - 1) DAY
        ),
        t.end_time
    ) AS `end_at`
FROM `timetable` t
JOIN `semester` s
  ON s.id = t.semester_id
 AND s.delete_at IS NULL
JOIN `bi_week_number` w
  ON w.week_number BETWEEN t.start_week AND t.end_week
WHERE t.delete_at IS NULL
  AND (
      t.week_type = 'Both'
      OR (t.week_type = 'Single' AND MOD(w.week_number, 2) = 1)
      OR (t.week_type = 'Double' AND MOD(w.week_number, 2) = 0)
  )
  AND DATE_ADD(
        DATE_SUB(s.start_date, INTERVAL WEEKDAY(s.start_date) DAY),
        INTERVAL ((w.week_number - 1) * 7 + t.weekday - 1) DAY
      ) BETWEEN s.start_date AND s.end_date;
