package xyz.jasenon.lab.base.service;

import com.baomidou.mybatisplus.extension.service.IService;
import xyz.jasenon.lab.common.model.base.User;


public interface UserService extends IService<User> {

    boolean isNameExsist( String name);

    User login(String username, String pwd);

    User registerNormalUser(User normalUser);

    User registerContactUser(User contactUser);

}
