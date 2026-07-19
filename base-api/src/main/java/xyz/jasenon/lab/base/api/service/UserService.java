package xyz.jasenon.lab.base.api.service;

import xyz.jasenon.lab.base.api.dto.ContactUserCreate;
import xyz.jasenon.lab.base.api.dto.UserCreate;
import xyz.jasenon.lab.base.api.dto.UserAuthorizationUpdate;
import xyz.jasenon.lab.base.api.dto.UserSession;
import xyz.jasenon.lab.base.api.model.User;

public interface UserService {

    boolean existsByName(String name);

    UserSession login(String username, String pwd);

    User registerNormalUser(UserCreate command);

    User registerContactUser(ContactUserCreate command);

    User updateUser(UserAuthorizationUpdate command);

}
