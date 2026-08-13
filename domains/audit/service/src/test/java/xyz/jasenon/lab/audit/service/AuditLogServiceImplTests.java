package xyz.jasenon.lab.audit.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import xyz.jasenon.lab.audit.api.model.AuditLogPageQuery;
import xyz.jasenon.lab.audit.persistence.AuditLogEntity;
import xyz.jasenon.lab.audit.persistence.mapper.AuditLogMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditLogServiceImplTests {

    @Test
    void buildsAllSupportedFilters() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(configuration, "audit-test"),
                AuditLogEntity.class
        );
        var query = new AuditLogPageQuery(
                2, 500, " user-1 ", null, null, " laboratory.update ", "EDIT",
                "laboratory", "lab-1", null, "编辑实验室", null, null,
                LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 8, 31, 23, 59)
        );

        String sql = AuditLogServiceImpl.pageWrapper(query).getTargetSql();

        assertTrue(sql.contains("subject_id"));
        assertTrue(sql.contains("operation"));
        assertTrue(sql.contains("actions"));
        assertTrue(sql.contains("object_types"));
        assertTrue(sql.contains("object_ids"));
        assertTrue(sql.contains("description"));
        assertTrue(sql.contains("occurred_at"));
        assertTrue(sql.contains("ORDER BY occurred_at DESC,id DESC"));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void pagesAuditLogsAndCapsPageSize() {
        AuditLogMapper mapper = mock(AuditLogMapper.class);
        AuditLogEntity entity = new AuditLogEntity();
        entity.setId("audit-1");
        entity.setSubjectId("user-1");
        entity.setOperation("laboratory.update");
        entity.setOccurredAt(LocalDateTime.of(2026, 8, 12, 10, 0));
        when(mapper.selectPage(any(IPage.class), any(Wrapper.class))).thenAnswer(invocation -> {
            Page<AuditLogEntity> page = invocation.getArgument(0);
            page.setRecords(List.of(entity));
            page.setTotal(1);
            return page;
        });

        var result = new AuditLogServiceImpl(mapper).page(new AuditLogPageQuery(
                2, 500, " user-1 ", null, null, " laboratory.update ", "EDIT",
                "laboratory", "lab-1", null, "编辑实验室", null, null,
                LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 8, 31, 23, 59)
        )).data();

        assertEquals(2, result.current());
        assertEquals(100, result.size());
        assertEquals(1, result.total());
        assertEquals("audit-1", result.records().get(0).id());

        ArgumentCaptor<IPage<AuditLogEntity>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
        verify(mapper).selectPage(pageCaptor.capture(), any(Wrapper.class));
        assertEquals(100, pageCaptor.getValue().getSize());
    }

    @Test
    @SuppressWarnings("rawtypes")
    void usesSafeDefaultsForNullQuery() {
        AuditLogMapper mapper = mock(AuditLogMapper.class);
        when(mapper.selectPage(any(IPage.class), any(Wrapper.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = new AuditLogServiceImpl(mapper).page(null).data();

        assertEquals(1, result.current());
        assertEquals(20, result.size());
    }
}
