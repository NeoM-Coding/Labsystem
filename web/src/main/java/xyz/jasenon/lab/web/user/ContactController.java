package xyz.jasenon.lab.web.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.jasenon.lab.base.api.dto.ContactUserCreate;
import xyz.jasenon.lab.base.api.model.User;
import xyz.jasenon.lab.base.api.service.UserService;
import xyz.jasenon.lab.common.util.R;
import xyz.jasenon.lab.observability.annotation.Traced;
import xyz.jasenon.lab.web.response.DiyResponseEntity;

@RestController
@RequestMapping("/api/contacts")
@Traced
@Tag(name = "联系人管理", description = "管理仅作为业务联系人存在且不能登录系统的用户资料")
public class ContactController {

    @DubboReference(check = false)
    private UserService userService;

    @PostMapping
    @Operation(summary = "创建联系人", description = "创建没有登录密码和系统权限的联系人资料。")
    public DiyResponseEntity<R<User>> create(@RequestBody ContactUserCreate command) {
        return DiyResponseEntity.of(R.success(userService.registerContactUser(command).mask()));
    }
}
