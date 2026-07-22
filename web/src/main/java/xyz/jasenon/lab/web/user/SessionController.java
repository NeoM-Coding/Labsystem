package xyz.jasenon.lab.web.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.jasenon.lab.base.api.dto.UserSession;
import xyz.jasenon.lab.base.api.service.UserService;
import xyz.jasenon.lab.common.util.R;
import xyz.jasenon.lab.observability.annotation.Traced;
import xyz.jasenon.lab.web.response.DiyResponseEntity;

@RestController
@RequestMapping("/api/sessions")
@Traced
@Tag(name = "用户会话", description = "处理用户身份认证和登录会话")
public class SessionController {

    @DubboReference(check = false)
    private UserService userService;

    @PostMapping
    @Operation(summary = "用户登录", description = "校验用户名和密码，创建 Sa-Token 会话并返回登录信息。")
    public DiyResponseEntity<R<UserSession>> login(@RequestBody UserLoginRequest request) {
        return DiyResponseEntity.of(R.success(
                userService.login(request.username(), request.password())
        ));
    }
}
