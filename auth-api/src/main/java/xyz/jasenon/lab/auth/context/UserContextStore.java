package xyz.jasenon.lab.auth.context;

import java.util.Optional;

public interface UserContextStore {

    void save(UserContext context);

    Optional<UserContext> find(String userId);

    void delete(String userId);
}
