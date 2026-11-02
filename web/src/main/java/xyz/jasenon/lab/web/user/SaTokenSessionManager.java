package xyz.jasenon.lab.web.user;

import xyz.jasenon.lab.common.error.ErrorCode;
import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;
import xyz.jasenon.lab.base.api.dto.UserSession;
import xyz.jasenon.lab.base.api.model.User;
import xyz.jasenon.lab.common.exception.BusinessException;

@Component
public class SaTokenSessionManager {



    public UserSession login(User user) {
        requireUser(user);

        try {
            StpUtil.login(user.getId());
            String tokenName = StpUtil.getTokenName();
            String tokenValue = StpUtil.getTokenValue();
            if (!hasText(tokenName) || !hasText(tokenValue)) {
                safeLogout();
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "登录会话创建失败");
            }
            return new UserSession(user, tokenName, tokenValue);
        } catch (SaTokenException exception) {
            throw sessionFailure("登录会话创建失败", ErrorCode.INTERNAL_ERROR, exception);
        }
    }

    public UserSession current(User user) {
        requireUser(user);
        try {
            StpUtil.checkLogin();
            String tokenName = StpUtil.getTokenName();
            String tokenValue = StpUtil.getTokenValue();
            if (!hasText(tokenName) || !hasText(tokenValue)) {
                throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED, "当前登录会话不存在");
            }
            return new UserSession(user, tokenName, tokenValue);
        } catch (SaTokenException exception) {
            throw sessionFailure("当前登录会话已失效", ErrorCode.AUTHENTICATION_REQUIRED, exception);
        }
    }

    public void logout() {
        try {
            // Sa-Token logout 对未登录请求是幂等的，同时会清理当前响应中的 token 信息。
            StpUtil.logout();
        } catch (SaTokenException exception) {
            throw sessionFailure("登录会话注销失败", ErrorCode.INTERNAL_ERROR, exception);
        }
    }

    private void requireUser(User user) {
        if (user == null || user.getId() == null || user.getId().isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "认证结果缺少用户标识");
        }
    }

    private void safeLogout() {
        try {
            StpUtil.logout();
        } catch (SaTokenException ignored) {
            // 创建结果无效时尽力回滚，原始失败信息由调用方返回。
        }
    }

    private BusinessException sessionFailure(String message, ErrorCode code, SaTokenException cause) {
        BusinessException exception = new BusinessException(code, message);
        exception.initCause(cause);
        return exception;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
