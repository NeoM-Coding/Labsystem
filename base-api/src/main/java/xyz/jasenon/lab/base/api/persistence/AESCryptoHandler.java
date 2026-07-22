package xyz.jasenon.lab.base.api.persistence;

import cn.hutool.crypto.symmetric.AES;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

import java.nio.charset.StandardCharsets;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

public class AESCryptoHandler implements TypeHandler<String> {

    private final AES aes = new AES(MybatisHandlerConfig.AES_KEY.getBytes(StandardCharsets.UTF_8));

    @Override
    public void setParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        if (parameter == null){
            ps.setNull(i, Types.VARCHAR);
            return;
        }
        ps.setString(i, encrypt(parameter));
    }

    @Override
    public String getResult(ResultSet rs, String columnName) throws SQLException {
        return decrypt(rs.getString(columnName));
    }

    @Override
    public String getResult(ResultSet rs, int columnIndex) throws SQLException {
        return decrypt(rs.getString(columnIndex));
    }

    @Override
    public String getResult(CallableStatement cs, int columnIndex) throws SQLException {
        return decrypt(cs.getString(columnIndex));
    }

    String encrypt(String plainText) {
        // Binary ciphertext must use a text-safe encoding before it is stored in VARCHAR.
        return aes.encryptBase64(plainText, StandardCharsets.UTF_8);
    }

    String decrypt(String cipherText) {
        if (cipherText == null) {
            return null;
        }
        return aes.decryptStr(cipherText, StandardCharsets.UTF_8);
    }
}
