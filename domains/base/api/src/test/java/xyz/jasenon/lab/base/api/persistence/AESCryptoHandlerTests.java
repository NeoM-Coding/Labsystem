package xyz.jasenon.lab.base.api.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AESCryptoHandlerTests {

    private AESCryptoHandler handler;

    @BeforeEach
    void setUp() {
        MybatisHandlerConfig.AES_KEY = "0123456789abcdef";
        handler = new AESCryptoHandler();
    }

    @Test
    void storesCiphertextAsBase64AndRestoresPlainText() {
        String cipherText = handler.encrypt("13800138000");

        assertThat(cipherText).matches("[A-Za-z0-9+/]+={0,2}");
        assertThat(handler.decrypt(cipherText)).isEqualTo("13800138000");
    }

    @Test
    void preservesNullDatabaseValue() {
        assertThat(handler.decrypt(null)).isNull();
    }
}
