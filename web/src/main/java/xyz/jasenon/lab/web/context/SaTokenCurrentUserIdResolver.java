package xyz.jasenon.lab.web.context;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SaTokenCurrentUserIdResolver implements CurrentUserIdResolver {

    @Override
    public Optional<String> currentUserId() {
        if (!StpUtil.isLogin()) {
            return Optional.empty();
        }
        return Optional.of(StpUtil.getLoginIdAsString());
    }
}
