package xyz.jasenon.lab.base.handler.audit.user;

import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.audit.api.AuditAction;
import xyz.jasenon.lab.auth.permission.RelationShip;
import xyz.jasenon.lab.base.api.dto.ContactUserCreate;
import xyz.jasenon.lab.base.api.dto.UserAuthorizationUpdate;
import xyz.jasenon.lab.base.api.dto.UserCreate;
import xyz.jasenon.lab.base.api.dto.UserDelete;
import xyz.jasenon.lab.base.api.model.User;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UserAuditHandlerTests {

    @Test
    void createHandlersUsePersistedUserIdWithoutLoggingSensitiveFields() {
        User result = User.builder().name("张三").build();
        result.setId("user-2");
        UserCreate user = new UserCreate(
                "张三", "zhangsan", "secret-password", "13800000000", null, null,
                Set.of(RelationShip.App.user_viewer), Set.of("lab-1")
        );
        ContactUserCreate contact = new ContactUserCreate(
                "李老师", "13900000000", "contact@example.com", null
        );

        var userFragment = new UserCreateAuditHandler().handle(user, result);
        var contactFragment = new ContactUserCreateAuditHandler().handle(contact, result);

        assertThat(userFragment.action()).isEqualTo(AuditAction.CREATE);
        assertThat(userFragment.objectId()).isEqualTo("user-2");
        assertThat(userFragment.description())
                .contains("张三", "1 项应用权限", "1 个实验室范围")
                .doesNotContain("secret-password", "13800000000");
        assertThat(contactFragment.objectType()).isEqualTo("contact");
        assertThat(contactFragment.description())
                .contains("李老师")
                .doesNotContain("13900000000", "contact@example.com");
    }

    @Test
    void updateHandlerUsesTargetUserId() {
        User target = User.builder().name("张三").build();
        target.setId("user-2");
        UserAuthorizationUpdate update = new UserAuthorizationUpdate(
                target, Set.of(RelationShip.App.user_manager), Set.of("lab-1", "lab-2")
        );

        var fragment = new UserUpdateAuditHandler().handle(update);

        assertThat(fragment.action()).isEqualTo(AuditAction.EDIT);
        assertThat(fragment.objectId()).isEqualTo("user-2");
        assertThat(fragment.description()).contains("张三", "1 项应用权限", "2 个实验室范围");
    }

    @Test
    void deleteHandlerUsesTargetUserIdAndDisplayName() {
        var fragment = new UserDeleteAuditHandler().handle(new UserDelete("user-2", "张三"));

        assertThat(fragment.action()).isEqualTo(AuditAction.DELETE);
        assertThat(fragment.objectType()).isEqualTo("user");
        assertThat(fragment.objectId()).isEqualTo("user-2");
        assertThat(fragment.description()).contains("张三");
    }
}
