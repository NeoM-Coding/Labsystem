package xyz.jasenon.lab.mqtt.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.jasenon.lab.common.config.MybatisPlusConfig;

@Configuration
public class Config {

    @Bean
    public MybatisPlusConfig mybatisPlusConfig(){
        return new MybatisPlusConfig();
    }

}
