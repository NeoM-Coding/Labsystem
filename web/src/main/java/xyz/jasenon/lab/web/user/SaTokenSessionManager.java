package xyz.jasenon.lab.web.user;

import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;
import xyz.jasenon.lab.base.api.dto.UserSession;
import xyz.jasenon.lab.base.api.model.User;
import xyz.jasenon.lab.common.exception.BusinessException;

@Component
public class SaTokenSessionManager {

    private static final int INTERNAL_SERVER_ERROR = 500;

    public UserSession login(User user) {
        requireUser(user);

        try {
            StpUtil.login(user.getId());
            String tokenName = StpUtil.getTokenName();
            String tokenValue = StpUtil.getTokenValue();
            if (!hasText(tokenName) || !hasText(tokenValue)) {
                safeLogout();
                throw new BusinessException(INTERNAL_SERVER_ERROR, "登录会话创建失败");
            }
            return new UserSession(user, tokenName, tokenValue);
        } catch (SaTokenException exception) {
            throw sessionFailure("登录会话创建失败", INTERNAL_SERVER_ERROR, exception);
        }
    }

    public UserSession current(User user) {
        requireUser(user);
        try {
            StpUtil.checkLogin();
            String tokenName = StpUtil.getTokenName();
            String tokenValue = StpUtil.getTokenValue();
            if (!hasText(tokenName) || !hasText(tokenValue)) {
                throw new BusinessException(401, "当前登录会话不存在");
            }
            return new UserSession(user, tokenName, tokenValue);
        } catch (SaTokenException exception) {
            throw sessionFailure("当前登录会话已失效", 401, exception);
        }
    }

    public void logout() {
        try {
            // Sa-Token logout 对未登录请求是幂等的，同时会清理当前响应中的 token 信息。
            StpUtil.logout();
        } catch (SaTokenException exception) {
            throw sessionFailure("登录会话注销失败", INTERNAL_SERVER_ERROR, exception);
        }
    }

    private void requireUser(User user) {
        if (user == null || user.getId() == null || user.getId().isBlank()) {
            throw new BusinessException(INTERNAL_SERVER_ERROR, "认证结果缺少用户标识");
        }
    }

    private void safeLogout() {
        try {
            StpUtil.logout();
        } catch (SaTokenException ignored) {
            // 创建结果无效时尽力回滚，原始失败信息由调用方返回。
        }
    }

    private BusinessException sessionFailure(String message, int code, SaTokenException cause) {
        BusinessException exception = new BusinessException(code, message);
        exception.initCause(cause);
        return exception;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
