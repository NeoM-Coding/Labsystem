package xyz.jasenon.lab.web.user;

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
public class SessionController {

    @DubboReference(check = false)
    private UserService userService;

    @PostMapping
    public DiyResponseEntity<R<UserSession>> login(@RequestBody UserLoginRequest request) {
        return DiyResponseEntity.of(R.success(
                userService.login(request.username(), request.password())
        ));
    }
}
