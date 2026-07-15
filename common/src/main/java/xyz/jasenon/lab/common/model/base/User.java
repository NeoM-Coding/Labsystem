package xyz.jasenon.lab.common.model.base;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.jasenon.lab.common.model.BaseEntity;
import xyz.jasenon.lab.common.model.Error;
import xyz.jasenon.lab.common.model.handler.AESCryptoHandler;
import xyz.jasenon.lab.common.model.handler.BCryptoHandler;

@EqualsAndHashCode(callSuper = true)
@TableName(value = "user", autoResultMap = true)
@Data
@Builder(toBuilder = true)
public class User extends BaseEntity {

    // 用户昵称  要求系统内unique （业务需求）
    private String name;
    // 用户名
    private String username;

    // 密码
    @TableField(typeHandler = BCryptoHandler.class)
    private String password;

    // 手机号
    @TableField(typeHandler = AESCryptoHandler.class)
    private String phone;

    // 邮箱
    private String email;

    // 备注
    private String mark;

    public User mask(){
        User u = this.toBuilder()
                .password("")
                .build();
        return u;
    }

    public Error vailidateNormalUser(){
        Error error = new Error();
        if (this.name.isBlank()){
            error.append("姓名不能为空");
        }
        if (this.username.isBlank()){
            error.append("用户名不能为空");
        }
        if (this.password.isBlank()){
            error.append("密码不能为空");
        }
        if (this.phone.isBlank() && this.email.isBlank()){
            error.append("手机号和邮箱必须择一填写");
        }
        return error;
    }

    public Error vailidateContactUser(){
        Error error = new Error();
        if (this.name.isBlank()){
            error.append("姓名不能为空");
        }
        if (this.phone.isBlank() && this.email.isBlank()){
            error.append("手机号和邮箱必须择一填写");
        }
        return error;
    }


}
