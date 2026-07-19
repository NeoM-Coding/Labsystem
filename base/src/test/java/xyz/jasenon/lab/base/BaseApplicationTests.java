package xyz.jasenon.lab.base;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "dubbo.registry.address=N/A",
        "dubbo.config-center.address=N/A",
        "dubbo.provider.export=false",
        "aes.key=0123456789abcdef"
})
class BaseApplicationTests {

    @Test
    void contextLoads() {
    }

}
