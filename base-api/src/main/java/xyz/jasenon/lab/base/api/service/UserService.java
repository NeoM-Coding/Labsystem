package xyz.jasenon.lab.base.api.service;

import xyz.jasenon.lab.base.api.dto.ContactUserCreate;
import xyz.jasenon.lab.base.api.dto.UserCreate;
import xyz.jasenon.lab.base.api.dto.UserAuthorizationUpdate;
import xyz.jasenon.lab.base.api.model.User;
import xyz.jasenon.lab.common.rpc.RpcResult;

public interface UserService {

    RpcResult<Boolean> existsByName(String name);

    RpcResult<User> authenticate(String username, String pwd);

    RpcResult<User> current();

    RpcResult<Void> logout();

    RpcResult<User> registerNormalUser(UserCreate command);

    RpcResult<User> registerContactUser(ContactUserCreate command);

    RpcResult<User> updateUser(UserAuthorizationUpdate command);

}
