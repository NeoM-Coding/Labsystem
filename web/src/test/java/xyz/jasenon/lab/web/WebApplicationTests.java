package xyz.jasenon.lab.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "dubbo.registry.address=N/A",
        "dubbo.config-center.address=N/A",
        "dubbo.consumer.check=false",
        "dubbo.consumer.init=false",
        "dubbo.consumer.lazy=true"
})
class WebApplicationTests {

    @Test
    void contextLoads() {
    }

}
