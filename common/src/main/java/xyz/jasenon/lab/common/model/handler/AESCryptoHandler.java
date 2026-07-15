package xyz.jasenon.lab.common.model.handler;

import cn.hutool.crypto.symmetric.AES;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

import java.nio.charset.StandardCharsets;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AESCryptoHandler implements TypeHandler<String> {

    private final AES aes = new AES(MybatisHandlerConfig.AES_KEY.getBytes(StandardCharsets.UTF_8));

    @Override
    public void setParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        if (parameter == null){
            return;
        }
        byte[] encrypt = aes.encrypt(parameter);
        ps.setString(i, new String(encrypt, StandardCharsets.UTF_8));
    }

    @Override
    public String getResult(ResultSet rs, String columnName) throws SQLException {
        String encrypt = rs.getNString(columnName);
        if (encrypt == null){
            return "";
        }
        return new String(aes.decrypt(encrypt), StandardCharsets.UTF_8);
    }

    @Override
    public String getResult(ResultSet rs, int columnIndex) throws SQLException {
        String encrypt = rs.getNString(columnIndex);
        if (encrypt == null){
            return "";
        }
        return new String(aes.decrypt(encrypt), StandardCharsets.UTF_8);
    }

    @Override
    public String getResult(CallableStatement cs, int columnIndex) throws SQLException {
        String encrypt = cs.getNString(columnIndex);
        if (encrypt == null){
            return "";
        }
        return new String(aes.decrypt(encrypt), StandardCharsets.UTF_8);
    }
}
