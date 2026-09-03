package xyz.jasenon.lab.bi;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableDubbo(scanBasePackages = "xyz.jasenon.lab.bi.service")
public class BiApplication {

    public static void main(String[] args) {
        SpringApplication.run(BiApplication.class, args);
    }
}
