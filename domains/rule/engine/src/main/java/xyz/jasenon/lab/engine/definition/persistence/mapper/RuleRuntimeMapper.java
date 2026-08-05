package xyz.jasenon.lab.engine.definition.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import xyz.jasenon.lab.engine.definition.persistence.model.RuleRuntimeEntity;

import java.time.Instant;

@Mapper
public interface RuleRuntimeMapper extends BaseMapper<RuleRuntimeEntity> {

    /**
     * 串行化同一 Runtime 的 revision 号分配，避免并发更新生成重复版本。
     */
    @Select("""
            SELECT published_revision_no
            FROM rule_runtime
            WHERE runtime_id = #{runtimeId} AND delete_at IS NULL
            FOR UPDATE
            """)
    Integer lockCurrentRevision(@Param("runtimeId") String runtimeId);

    @Update("""
            UPDATE rule_runtime
            SET enabled = #{enabled},
                status = #{status},
                published_revision_no = #{revisionNo},
                active_from = #{activeFrom},
                active_until = #{activeUntil},
                update_at = CURRENT_TIMESTAMP(3)
            WHERE runtime_id = #{runtimeId} AND delete_at IS NULL
            """)
    int publishRevision(
            @Param("runtimeId") String runtimeId,
            @Param("enabled") boolean enabled,
            @Param("status") String status,
            @Param("revisionNo") int revisionNo,
            @Param("activeFrom") Instant activeFrom,
            @Param("activeUntil") Instant activeUntil
    );

    @Update("""
            UPDATE rule_runtime
            SET enabled = #{enabled},
                status = #{status},
                published_revision_no = #{revisionNo},
                update_at = CURRENT_TIMESTAMP(3)
            WHERE runtime_id = #{runtimeId} AND delete_at IS NULL
            """)
    int publishEnabledRevision(
            @Param("runtimeId") String runtimeId,
            @Param("enabled") boolean enabled,
            @Param("status") String status,
            @Param("revisionNo") int revisionNo
    );

    @Update("""
            UPDATE rule_runtime
            SET enabled = 0,
                status = 'DISABLED',
                delete_at = CURRENT_TIMESTAMP(3),
                update_at = CURRENT_TIMESTAMP(3)
            WHERE runtime_id = #{runtimeId} AND delete_at IS NULL
            """)
    int softDelete(@Param("runtimeId") String runtimeId);
}
