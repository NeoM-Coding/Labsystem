package xyz.jasenon.lab.base.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;
import xyz.jasenon.lab.base.mapper.UserMapper;
import xyz.jasenon.lab.base.api.service.UserService;
import xyz.jasenon.lab.base.util.SaTokenUtil;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.base.api.validation.ValidationErrors;
import xyz.jasenon.lab.base.api.model.User;

@DubboService
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final int BAD_REQUEST = 400;
    private static final int FORBIDDEN = 403;
    private static final int NOT_FOUND = 404;
    private static final int INTERNAL_SERVER_ERROR = 500;

    @Override
    public boolean existsByName(String name) {
        return this.baseMapper.isNameExsist(name);
    }

    @Override
    public User login(String username, String pwd) {
        User user = this.baseMapper.getUserByUsername(username);
        if (user == null){
            throw new BusinessException(NOT_FOUND, "用户不存在!");
        }

        if (user.getPassword() == null || user.getPassword().isEmpty()){
            throw new BusinessException(FORBIDDEN, "该用户不允许使用系统，请联系管理员!");
        }

        if (!BCrypt.checkpw(pwd,user.getPassword())){
            throw new BusinessException(FORBIDDEN, "密码错误!");
        }
        SaTokenUtil.login(user.getId());
        return user;
    }

    @Override
    public User registerNormalUser(User normalUser) {
        ValidationErrors errors = normalUser.validateNormalUser();
        if (errors.hasErrors()) {
            String errorStr = String.join(",", errors.errors());
            throw new BusinessException(BAD_REQUEST, errorStr);
        }

        synchronized (normalUser.getName().intern()){
            boolean exsist = existsByName(normalUser.getName());
            if (exsist) {
                throw new BusinessException(FORBIDDEN, "该姓名已被使用");
            }
            try {
                save(normalUser);
            }catch (Exception e){
                throw new BusinessException(INTERNAL_SERVER_ERROR, "插入失败");
            }
        }
        return normalUser;
    }

    @Override
    public User registerContactUser(User contactUser) {
        ValidationErrors errors = contactUser.validateContactUser();
        if (errors.hasErrors()) {
            String errorStr = String.join(",", errors.errors());
            throw new BusinessException(BAD_REQUEST, errorStr);
        }

        synchronized (contactUser.getName().intern()){
            boolean exsist = existsByName(contactUser.getName());
            if (exsist){
                throw new BusinessException(FORBIDDEN, "该姓名已被使用");
            }
            try {
                save(contactUser);
            }catch (Exception e){
                throw new BusinessException(INTERNAL_SERVER_ERROR, "插入失败");
            }
        }
        return contactUser;
    }


}
