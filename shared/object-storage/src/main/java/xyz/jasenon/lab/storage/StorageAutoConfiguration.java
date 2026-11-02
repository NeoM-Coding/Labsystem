package xyz.jasenon.lab.storage;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
@AutoConfiguration
@EnableConfigurationProperties(StorageProperties.class)
@ConditionalOnProperty(name = "lab.storage.enabled", havingValue = "true")
public class StorageAutoConfiguration {
    @Bean @ConditionalOnMissingBean
    ObjectStorage objectStorage(StorageProperties properties) { return new MinioObjectStorage(properties); }
}
