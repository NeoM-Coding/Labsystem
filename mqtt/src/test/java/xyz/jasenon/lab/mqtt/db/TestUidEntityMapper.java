package xyz.jasenon.lab.mqtt.db;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TestUidEntityMapper extends BaseMapper<TestUidEntity> {

    @Insert("INSERT INTO test_uid_entity (id, name) VALUES (#{entity.id}, #{entity.name})")
    int insertCustom(@Param("entity") TestUidEntity entity);
}
