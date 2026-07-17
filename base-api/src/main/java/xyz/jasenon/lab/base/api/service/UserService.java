package xyz.jasenon.lab.base.api.service;

import xyz.jasenon.lab.base.api.model.User;

public interface UserService {

    boolean existsByName(String name);

    User login(String username, String pwd);

    User registerNormalUser(User normalUser);

    User registerContactUser(User contactUser);

}
