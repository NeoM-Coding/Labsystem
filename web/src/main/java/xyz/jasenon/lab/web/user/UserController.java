package xyz.jasenon.lab.web.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.jasenon.lab.base.api.dto.UserAuthorizationUpdate;
import xyz.jasenon.lab.base.api.dto.UserCreate;
import xyz.jasenon.lab.base.api.dto.UserListQuery;
import xyz.jasenon.lab.base.api.model.User;
import xyz.jasenon.lab.base.api.service.UserService;
import xyz.jasenon.lab.common.util.R;
import xyz.jasenon.lab.observability.annotation.Traced;
import xyz.jasenon.lab.observability.rpc.RpcClient;
import xyz.jasenon.lab.web.response.DiyResponseEntity;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@Traced
@Tag(name = "用户管理", description = "管理系统用户、联系人及其应用和实验室权限")
public class UserController {

    @DubboReference(check = false)
    private UserService userService;

    @GetMapping
    @Operation(
            summary = "查询用户与联系人",
            description = "查询未删除的系统用户和联系人，可按姓名、用户名或邮箱模糊搜索。"
                    + "需要 app:global 的 list_user 权限，该权限由 user_manager、user_viewer 或 super_admin 授予。"
                    + "响应不会包含密码。"
    )
    public DiyResponseEntity<R<List<User>>> list(
            @RequestParam(required = false) String keyword) {
        List<User> users = RpcClient.call(() -> userService.list(new UserListQuery(keyword)));
        return DiyResponseEntity.of(R.success(users));
    }

    @PostMapping
    @Operation(summary = "创建系统用户", description = "创建可登录的系统用户，并为其分配应用权限和实验室访问范围。")
    public DiyResponseEntity<R<User>> create(@RequestBody UserCreate command) {
        User user = RpcClient.call(() -> userService.registerNormalUser(command));
        return DiyResponseEntity.of(R.success(user.mask()));
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
        User updated = RpcClient.call(() -> userService.updateUser(downstream));
        return DiyResponseEntity.of(R.success(updated.mask()));
    }
}
