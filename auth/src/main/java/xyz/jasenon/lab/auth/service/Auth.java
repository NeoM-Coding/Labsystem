package xyz.jasenon.lab.auth.service;

import xyz.jasenon.lab.auth.command.ActionCommand;
import xyz.jasenon.lab.auth.command.GrantCommand;
import xyz.jasenon.lab.auth.command.RevokeCommand;
import xyz.jasenon.lab.auth.command.UserAuthorizationCommand;

import java.util.Set;

public interface Auth {

    void grant(GrantCommand grantCommand);

    void revoke(RevokeCommand revokeCommand);

    boolean check(ActionCommand actionCommand);

    void synchronize(UserAuthorizationCommand command);

    Set<String> visibleLaboratoryIds(String userId);

}
