package xyz.jasenon.lab.quartz;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import xyz.jasenon.lab.engine.RuleEngineApplication;

@SpringBootTest(
        classes = RuleEngineApplication.class,
        properties = {
                "lab.redis.enabled=false",
                "server.port=0"
        }
)
class RuleEngineApplicationTests {

    @Test
    void contextLoads() {
    }

}
