package xyz.jasenon.lab.bi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class BiConfiguration {

    @Bean
    Clock biClock() {
        return Clock.systemDefaultZone();
    }
}
