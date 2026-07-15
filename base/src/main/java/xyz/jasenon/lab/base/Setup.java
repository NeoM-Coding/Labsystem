package xyz.jasenon.lab.base;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import xyz.jasenon.lab.redis.core.RedisBus;

@Component
@AllArgsConstructor
public class Setup {

    private final RedisBus redisBus;


}
