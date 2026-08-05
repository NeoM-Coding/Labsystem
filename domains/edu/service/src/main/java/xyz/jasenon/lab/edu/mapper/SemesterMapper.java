package xyz.jasenon.lab.edu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import xyz.jasenon.lab.edu.model.Semester;

@Mapper
public interface SemesterMapper extends BaseMapper<Semester> {

    @Select("SELECT * FROM semester WHERE id = #{semesterId} FOR UPDATE")
    Semester selectByIdForUpdate(@Param("semesterId") String semesterId);
}
