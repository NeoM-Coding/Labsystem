package xyz.jasenon.lab;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import xyz.jasenon.lab.engine.RuleEngineApplication;

@SpringBootTest(
        classes = RuleEngineApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "lab.redis.enabled=false",
                "lab.rule-engine.persistence.enabled=false",
                "dubbo.registry.address=N/A",
                "dubbo.config-center.address=N/A",
                "spring.profiles.active=test",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
                "fun.uid.assigner-mode=none"
        }
)
class RuleEngineApplicationTests {

    @Test
    void contextLoads() {
    }

}
