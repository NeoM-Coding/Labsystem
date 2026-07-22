package xyz.jasenon.lab.web.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.jasenon.lab.base.api.dto.UserAuthorizationUpdate;
import xyz.jasenon.lab.base.api.dto.UserCreate;
import xyz.jasenon.lab.base.api.model.User;
import xyz.jasenon.lab.base.api.service.UserService;
import xyz.jasenon.lab.common.util.R;
import xyz.jasenon.lab.observability.annotation.Traced;
import xyz.jasenon.lab.web.response.DiyResponseEntity;

@RestController
@RequestMapping("/api/users")
@Traced
@Tag(name = "用户管理", description = "管理能够登录系统的用户及其应用和实验室权限")
public class UserController {

    @DubboReference(check = false)
    private UserService userService;

    @PostMapping
    @Operation(summary = "创建系统用户", description = "创建可登录的系统用户，并为其分配应用权限和实验室访问范围。")
    public DiyResponseEntity<R<User>> create(@RequestBody UserCreate command) {
        return DiyResponseEntity.of(R.success(userService.registerNormalUser(command).mask()));
    }

    @PutMapping("/{userId}")
    @Operation(summary = "修改系统用户", description = "更新指定用户资料，并同步调整应用权限和实验室访问范围。")
    public DiyResponseEntity<R<User>> update(@PathVariable String userId,
                                             @RequestBody UserAuthorizationUpdate command) {
        // URL 是资源身份的唯一来源，忽略客户端在 body 中携带的用户 ID。
        User user = command.user();
        if (user != null) {
            user.setId(userId);
        }
        UserAuthorizationUpdate downstream = new UserAuthorizationUpdate(
                user, command.appRelations(), command.laboratoryIds()
        );
        return DiyResponseEntity.of(R.success(userService.updateUser(downstream).mask()));
    }
}
