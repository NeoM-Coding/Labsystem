package xyz.jasenon.lab.base.api.persistence;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        value = "aes.key",
        matchIfMissing = false
)
public class MybatisHandlerConfig {

    public static String AES_KEY;

    @Value("${aes.key}")
    public void setAesKey(String key){
        AES_KEY = key;
    }

}
