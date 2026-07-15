package xyz.jasenon.lab.base.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import xyz.jasenon.lab.base.mapper.UserMapper;
import xyz.jasenon.lab.base.service.UserService;
import xyz.jasenon.lab.base.util.SaTokenUtil;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.common.model.Error;
import xyz.jasenon.lab.common.model.base.User;

@DubboService
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    @Override
    public boolean isNameExsist(String name) {
        return this.baseMapper.isNameExsist(name);
    }

    @Override
    public User login(String username, String pwd) {
        User user = this.baseMapper.getUserByUsername(username);
        if (user == null){
            throw new BusinessException(HttpStatus.NOT_FOUND.value(),"用户不存在!");
        }

        if (user.getPassword() == null || user.getPassword().isEmpty()){
            throw new BusinessException(HttpStatus.FORBIDDEN.value(), "该用户不允许使用系统，请联系管理员!");
        }

        if (!BCrypt.checkpw(pwd,user.getPassword())){
            throw new BusinessException(HttpStatus.FORBIDDEN.value(), "密码错误!");
        }
        SaTokenUtil.login(user.getId());
        return user;
    }

    @Override
    public User registerNormalUser(User normalUser) {
        Error error = normalUser.vailidateNormalUser();
        if (error.error()){
            String errorStr = String.join(",",error.errors());
            throw new BusinessException(HttpStatus.BAD_REQUEST.value(), errorStr);
        }

        synchronized (normalUser.getName().intern()){
            boolean exsist = isNameExsist(normalUser.getName());
            if (exsist) {
                throw new BusinessException(HttpStatus.FORBIDDEN.value(), "该姓名已被使用");
            }
            try {
                save(normalUser);
            }catch (Exception e){
                throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR.value(), "插入失败");
            }
        }
        return normalUser;
    }

    @Override
    public User registerContactUser(User contactUser) {
        Error error = contactUser.vailidateContactUser();
        if (error.error()){
            String errorStr = String.join(",",error.errors());
            throw new BusinessException(HttpStatus.BAD_REQUEST.value(), errorStr);
        }

        synchronized (contactUser.getName().intern()){
            boolean exsist = isNameExsist(contactUser.getName());
            if (exsist){
                throw new BusinessException(HttpStatus.FORBIDDEN.value(), "该姓名已被使用");
            }
            try {
                save(contactUser);
            }catch (Exception e){
                throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR.value(), "插入失败");
            }
        }
        return contactUser;
    }


}
