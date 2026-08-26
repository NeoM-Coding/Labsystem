package xyz.jasenon.lab.base.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.base.api.persistence.AESCryptoHandler;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class UserMapperXmlTests {

    private static final String NAMESPACE = "xyz.jasenon.lab.base.mapper.UserMapper.";

    @Test
    void customUserQueriesDecodeEncryptedPhoneFields() throws IOException {
        Configuration configuration = parse("mapper/UserMapper.xml");
        ResultMap userResultMap = configuration.getResultMap(NAMESPACE + "userResultMap");
        ResultMapping phoneMapping = userResultMap.getResultMappings().stream()
                .filter(mapping -> "phone".equals(mapping.getProperty()))
                .findFirst()
                .orElseThrow();

        assertInstanceOf(AESCryptoHandler.class, phoneMapping.getTypeHandler());
        assertUsesResultMap(configuration, "getUserByUsername", userResultMap);
        assertUsesResultMap(configuration, "listUsers", userResultMap);
    }

    private void assertUsesResultMap(
            Configuration configuration,
            String statementId,
            ResultMap expected
    ) {
        MappedStatement statement = configuration.getMappedStatement(NAMESPACE + statementId);

        assertEquals(expected.getId(), statement.getResultMaps().get(0).getId());
    }

    private Configuration parse(String resource) throws IOException {
        Configuration configuration = new Configuration();
        configuration.getTypeHandlerRegistry()
                .register(new AESCryptoHandler("0123456789abcdef"));
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(
                    input,
                    configuration,
                    resource,
                    configuration.getSqlFragments()
            ).parse();
        }
        return configuration;
    }
}
