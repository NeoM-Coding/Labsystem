package xyz.jasenon.lab.storage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;
class StorageConfigurationTests {
    private final ApplicationContextRunner context=new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(StorageAutoConfiguration.class));
    @Test void disabledByDefaultAndStartsWithoutNetworkWhenConfigured() {
        context.run(c->assertThat(c).doesNotHaveBean(ObjectStorage.class));
        context.withPropertyValues("lab.storage.enabled=true","lab.storage.access-key=test","lab.storage.secret-key=testsecret",
            "lab.storage.public-endpoint=http://device-network:9000").run(c-> {
                assertThat(c).hasSingleBean(ObjectStorage.class);
                assertThat(c.getBean(StorageProperties.class).publicEndpoint()).isEqualTo("http://device-network:9000");
            });
    }
}
