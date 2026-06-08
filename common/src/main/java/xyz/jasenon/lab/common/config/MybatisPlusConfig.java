package xyz.jasenon.lab.common.config;

import io.github.sunjieyi60.uid.starter.UidGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.context.annotation.Bean;

public class MybatisPlusConfig {

    @Bean
    public CustomIdGenerator customIdGenerator(UidGenerator uidGenerator){
        return new CustomIdGenerator(uidGenerator);
    }

    public class CustomIdGenerator implements IdentifierGenerator {
        private final UidGenerator generator;
        public CustomIdGenerator(UidGenerator generator){
            this.generator = generator;
        }

        @Override
        public Number nextId(Object entity) {
            return this.generator.getUID();
        }
    }

}
