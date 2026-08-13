package xyz.jasenon.lab.engine.alert.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import xyz.jasenon.lab.engine.alert.persistence.model.AlertLogEntity;

@Mapper
public interface AlertLogMapper extends BaseMapper<AlertLogEntity> {
}
