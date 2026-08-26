package xyz.jasenon.lab.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import xyz.jasenon.lab.base.api.model.CompensationTask;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CompensationTaskMapper extends BaseMapper<CompensationTask> {
    @Select("SELECT * FROM compensation_task WHERE enabled=1 AND delete_at IS NULL " +
            "AND next_fire_at<=#{now} AND (lock_until IS NULL OR lock_until<#{now}) ORDER BY next_fire_at LIMIT 20")
    List<CompensationTask> findDue(@Param("now") LocalDateTime now);

    @Update("UPDATE compensation_task SET locked_by=#{owner},lock_until=#{until} WHERE id=#{id} " +
            "AND enabled=1 AND delete_at IS NULL AND next_fire_at<=#{now} AND (lock_until IS NULL OR lock_until<#{now})")
    int claim(@Param("id") String id, @Param("owner") String owner,
              @Param("now") LocalDateTime now, @Param("until") LocalDateTime until);
}
