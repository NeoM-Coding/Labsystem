package xyz.jasenon.lab.web.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;
import xyz.jasenon.lab.base.api.dto.UserSession;
import xyz.jasenon.lab.base.api.model.User;
import xyz.jasenon.lab.base.api.service.UserService;
import xyz.jasenon.lab.common.util.R;
import xyz.jasenon.lab.observability.annotation.Traced;
import xyz.jasenon.lab.observability.rpc.RpcClient;
import xyz.jasenon.lab.web.response.DiyResponseEntity;

@RestController
@RequestMapping("/api/sessions")
@Traced
@Tag(name = "用户会话", description = "处理用户身份认证和登录会话")
public class SessionController {

    @DubboReference(check = false)
    private UserService userService;

    @Autowired
    private SaTokenSessionManager sessionManager;

    @Value("${lab.session-cookie.secure:false}")
    private boolean secureCookie;

    @PostMapping
    @Operation(summary = "用户登录", description = "校验用户名和密码，创建 Sa-Token 会话并返回登录信息。")
    public DiyResponseEntity<R<UserSession>> login(@RequestBody UserLoginRequest request,
                                                   HttpServletResponse resp) {
        User user = RpcClient.call(
                () -> userService.authenticate(request.username(), request.password())
        );
        UserSession session = sessionManager.login(user);
        ResponseCookie cookie = ResponseCookie.from(session.tokenName(), session.tokenValue())
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/")
                .build();
        resp.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return DiyResponseEntity.of(R.success(
            session
        ));
    }

    @GetMapping("/current")
    @Operation(summary = "获取当前会话", description = "根据当前请求携带的 cookie 获取用户信息和 Sa-Token 会话信息。")
    public DiyResponseEntity<R<UserSession>> current() {
        User user = RpcClient.call(userService::current);
        return DiyResponseEntity.of(R.success(sessionManager.current(user)));
    }

    @DeleteMapping
    @Operation(summary = "用户登出", description = "用户携带cookie 登出，清除 cookie, redis 缓存。")
    public DiyResponseEntity<R<Void>> logout(HttpServletResponse resp){
        RpcClient.run(userService::logout);
        sessionManager.logout();
        return DiyResponseEntity.of(
                R.success()
        );
    }

}
