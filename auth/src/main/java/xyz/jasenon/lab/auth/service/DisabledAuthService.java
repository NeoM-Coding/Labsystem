package xyz.jasenon.lab.auth.service;

import xyz.jasenon.lab.auth.command.ActionCommand;
import xyz.jasenon.lab.auth.command.GrantCommand;
import xyz.jasenon.lab.auth.command.RevokeCommand;
import xyz.jasenon.lab.auth.command.UserAuthorizationCommand;

import java.util.Set;

public class DisabledAuthService implements Auth {

    @Override
    public void grant(GrantCommand grantCommand) {
        throw disabled();
    }

    @Override
    public void revoke(RevokeCommand revokeCommand) {
        throw disabled();
    }

    @Override
    public boolean check(ActionCommand actionCommand) {
        return true;
    }

    @Override
    public void synchronize(UserAuthorizationCommand command) {
        throw disabled();
    }

    @Override
    public Set<String> visibleLaboratoryIds(String userId) {
        return Set.of();
    }

    private static IllegalStateException disabled() {
        return new IllegalStateException("Permify 授权未启用，无法修改用户权限");
    }
}
