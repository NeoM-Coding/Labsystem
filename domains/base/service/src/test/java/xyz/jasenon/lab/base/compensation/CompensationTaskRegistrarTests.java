package xyz.jasenon.lab.base.compensation;

import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.base.api.model.CompensationTask;
import xyz.jasenon.lab.base.mapper.CompensationTaskMapper;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CompensationTaskRegistrarTests {
    @Test
    void registersAnnotatedHandlerOnlyOnFirstStartup() {
        CompensationTaskMapper mapper = mock(CompensationTaskMapper.class);
        when(mapper.selectCount(any())).thenReturn(0L);
        CompensationTaskRegistrar registrar = new CompensationTaskRegistrar(mapper, List.of(new DemoHandler()));

        registrar.registerMissingTasks();

        var captor = org.mockito.ArgumentCaptor.forClass(CompensationTask.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("demo-reconcile");
        assertThat(captor.getValue().getCronExpression()).isEqualTo("0 0 0 * * *");
        assertThat(captor.getValue().getNextFireAt()).isNotNull();
    }

    @CompensationJob(code = "demo-reconcile", cron = "0 0 0 * * *")
    static class DemoHandler implements CompensationTaskHandler {
        public String type() { return "demo-reconcile"; }
        public CompensationResult execute(Map<String, Object> payload) { return CompensationResult.success(0, "ok"); }
    }
}
