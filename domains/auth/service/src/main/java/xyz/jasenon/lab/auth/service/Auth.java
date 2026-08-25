package xyz.jasenon.lab.auth.service;

import xyz.jasenon.lab.auth.command.ActionCommand;
import xyz.jasenon.lab.auth.command.GrantCommand;
import xyz.jasenon.lab.auth.command.RevokeCommand;
import xyz.jasenon.lab.auth.command.UserAuthorizationCommand;
import xyz.jasenon.lab.auth.permission.Permission;

import java.util.List;

public interface Auth {

    void grant(GrantCommand grantCommand);

    void revoke(RevokeCommand revokeCommand);

    boolean check(ActionCommand actionCommand);

    void synchronize(UserAuthorizationCommand command);

    void removeUser(String userId);

    List<Permission> list(String userId);

}
