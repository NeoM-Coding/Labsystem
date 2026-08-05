package xyz.jasenon.lab.base.api.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import xyz.jasenon.lab.persistence.model.BaseEntity;
import xyz.jasenon.lab.base.api.validation.ValidationErrors;
import xyz.jasenon.lab.base.api.persistence.AESCryptoHandler;
import xyz.jasenon.lab.base.api.persistence.BCryptoHandler;

import java.io.Serial;

@EqualsAndHashCode(callSuper = true)
@TableName(value = "user", autoResultMap = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class User extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

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

    public User mask() {
        User masked = this.toBuilder()
                .password("")
                .build();
        masked.setId(getId());
        masked.setCreateAt(getCreateAt());
        masked.setUpdateAt(getUpdateAt());
        masked.setDeleteAt(getDeleteAt());
        return masked;
    }

    public ValidationErrors validateNormalUser() {
        ValidationErrors errors = new ValidationErrors();
        if (isBlank(name)) {
            errors.append("姓名不能为空");
        }
        if (isBlank(username)) {
            errors.append("用户名不能为空");
        }
        if (isBlank(password)) {
            errors.append("密码不能为空");
        }
        if (isBlank(phone) && isBlank(email)) {
            errors.append("手机号和邮箱必须择一填写");
        }
        return errors;
    }

    public ValidationErrors validateContactUser() {
        ValidationErrors errors = new ValidationErrors();
        if (isBlank(name)) {
            errors.append("姓名不能为空");
        }
        if (isBlank(phone) && isBlank(email)) {
            errors.append("手机号和邮箱必须择一填写");
        }
        return errors;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
