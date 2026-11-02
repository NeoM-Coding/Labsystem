package xyz.jasenon.lab.bi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@org.springframework.boot.context.properties.EnableConfigurationProperties(xyz.jasenon.lab.storage.StorageProperties.class)
@Configuration
public class BiConfiguration {

    @Bean
    Clock biClock() {
        return Clock.system(java.time.ZoneId.of("Asia/Shanghai"));
    }
}
