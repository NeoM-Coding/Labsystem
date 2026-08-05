package xyz.jasenon.lab.edu.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import xyz.jasenon.lab.persistence.model.BaseEntity;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@TableName("semester")
public class Semester extends BaseEntity {

    /**
     * 学期名称，2025-2026-1 存在正则(\\d{4})-(\\d{4}) 第(\\d+)学年
     */
    private String name;

    /**
     * 学期开始时间
     */
    private LocalDate startDate;

    /**
     * 学期结束时间
     */
    private LocalDate endDate;

    public Semester(Semester source) {
        setId(source.getId());
        setCreateAt(source.getCreateAt());
        setUpdateAt(source.getUpdateAt());
        setDeleteAt(source.getDeleteAt());
        this.name = source.name;
        this.startDate = source.startDate;
        this.endDate = source.endDate;
    }
}
