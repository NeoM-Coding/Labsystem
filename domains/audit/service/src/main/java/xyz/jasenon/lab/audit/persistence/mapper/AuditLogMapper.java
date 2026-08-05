package xyz.jasenon.lab.audit.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import xyz.jasenon.lab.audit.persistence.AuditLogEntity;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogEntity> {
}
