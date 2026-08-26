package xyz.jasenon.lab.base.api.persistence;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        value = "aes.key",
        matchIfMissing = false
)
public class MybatisHandlerConfig implements ConfigurationCustomizer {

    private final String aesKey;

    public MybatisHandlerConfig(@Value("${aes.key}") String aesKey) {
        if (aesKey == null || aesKey.isBlank()) {
            throw new IllegalStateException("aes.key 不能为空");
        }
        this.aesKey = aesKey;
    }

    @Override
    public void customize(MybatisConfiguration configuration) {
        // Mapper XML 解析前注册已配置实例，避免 MyBatis 通过无参构造器提前创建 Handler。
        configuration.getTypeHandlerRegistry().register(new AESCryptoHandler(aesKey));
    }

}
