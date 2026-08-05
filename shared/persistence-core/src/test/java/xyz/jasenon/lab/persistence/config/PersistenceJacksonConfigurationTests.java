package xyz.jasenon.lab.persistence.config;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceJacksonConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    PersistenceJacksonConfiguration.class
            ));

    @Test
    void configuresJavaTimeOnGlobalObjectMapperAndMybatisTypeHandler() {
        ObjectMapper original = JacksonTypeHandler.getObjectMapper();

        try {
            contextRunner.run(context -> {
                ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
                Snapshot snapshot = new Snapshot(
                        LocalDate.of(2026, 9, 1),
                        LocalDateTime.of(2026, 7, 30, 20, 0)
                );

                String globalJson = assertDoesNotThrow(() -> objectMapper.writeValueAsString(snapshot));
                JacksonTypeHandler handler = new JacksonTypeHandler(Snapshot.class);
                String typeHandlerJson = handler.toJson(snapshot);
                Snapshot restored = (Snapshot) handler.parse(typeHandlerJson);

                assertTrue(globalJson.contains("\"createAt\""));
                assertEquals(objectMapper, JacksonTypeHandler.getObjectMapper());
                assertEquals(snapshot, restored);
            });
        } finally {
            JacksonTypeHandler.setObjectMapper(original);
        }
    }

    private record Snapshot(LocalDate startDate, LocalDateTime createAt) {
    }
}
