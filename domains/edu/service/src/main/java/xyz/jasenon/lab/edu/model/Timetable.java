package xyz.jasenon.lab.edu.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import xyz.jasenon.lab.edu.api.model.WeekType;
import xyz.jasenon.lab.persistence.model.BaseEntity;

import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@TableName(value = "timetable", autoResultMap = true)
public class Timetable extends BaseEntity {

    // 学期id
    private String semesterId;

    // 学期信息
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Semester semesterInfo;

    // 上课实验室
    private String laboratoryId;

    // 课程名称
    private String courseName;

    // 教师名称
    private String teacherName;

    /**
     * 周次类型
     */
    private WeekType weekType;

    /**
     * 起始周
     */
    private Integer startWeek;

    /**
     * 结束周
     */
    private Integer endWeek;

    /**
     * 课表展示的开始节次
     */
    @TableField("start_section")
    private Integer startSection;

    /**
     * 课表展示的结束节次
     */
    @TableField("end_section")
    private Integer endSection;

    /**
     * 开始时间
     */
    private LocalTime startTime;

    /**
     * 结束时间
     */
    private LocalTime endTime;

    // 上课星期 (1-7,0-6 代表周一到周天)
    private Integer weekday;

}
