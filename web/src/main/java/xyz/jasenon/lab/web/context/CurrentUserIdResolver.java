package xyz.jasenon.lab.web.context;

import java.util.Optional;

public interface CurrentUserIdResolver {

    Optional<String> currentUserId();
}
