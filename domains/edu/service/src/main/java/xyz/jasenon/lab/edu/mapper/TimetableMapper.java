package xyz.jasenon.lab.edu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import xyz.jasenon.lab.edu.model.Timetable;

@Mapper
public interface TimetableMapper extends BaseMapper<Timetable> {
}
