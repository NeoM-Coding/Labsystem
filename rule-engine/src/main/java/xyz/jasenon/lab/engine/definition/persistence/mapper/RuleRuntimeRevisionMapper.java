package xyz.jasenon.lab.engine.definition.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import xyz.jasenon.lab.engine.definition.persistence.model.CurrentRuntimeRevision;
import xyz.jasenon.lab.engine.definition.persistence.model.RuleRuntimeRevisionEntity;

import java.util.List;

@Mapper
public interface RuleRuntimeRevisionMapper extends BaseMapper<RuleRuntimeRevisionEntity> {

    @Select("""
            SELECT m.runtime_id, m.enabled, r.definition
            FROM rule_runtime m
            JOIN rule_runtime_revision r
              ON r.runtime_id = m.runtime_id
             AND r.revision_no = m.published_revision_no
            WHERE m.runtime_id = #{runtimeId} AND m.delete_at IS NULL
            """)
    CurrentRuntimeRevision selectCurrent(@Param("runtimeId") String runtimeId);

    @Select("""
            SELECT m.runtime_id, m.enabled, r.definition
            FROM rule_runtime m
            JOIN rule_runtime_revision r
              ON r.runtime_id = m.runtime_id
             AND r.revision_no = m.published_revision_no
            WHERE m.delete_at IS NULL
            ORDER BY m.id
            """)
    List<CurrentRuntimeRevision> selectAllCurrent();
}
