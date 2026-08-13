package xyz.jasenon.lab.base.handler.authorization.user;

import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.permission.Action;
import xyz.jasenon.lab.auth.permission.RelationShip;
import xyz.jasenon.lab.base.api.dto.ContactUserCreate;
import xyz.jasenon.lab.base.api.dto.UserAuthorizationUpdate;
import xyz.jasenon.lab.base.api.dto.UserCreate;
import xyz.jasenon.lab.base.api.dto.UserListQuery;
import xyz.jasenon.lab.base.api.dto.UserDelete;
import xyz.jasenon.lab.base.api.model.User;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserAuthorizationActionHandlerTests {

    private final UserContext context = UserContext.of(
            "operator", "operator", "Operator", Set.of()
    );

    @Test
    void createDtosRequireCreateUserAction() {
        UserCreate user = new UserCreate(
                "New User", "new-user", "password", null, "new@example.com", null,
                Set.of(RelationShip.App.user_viewer), Set.of("lab-1")
        );
        ContactUserCreate contact = new ContactUserCreate(
                "Contact", "13800000000", null, "Emergency contact"
        );

        assertEquals(Action.App.create_user, new UserCreateActionHandler().handle(user, context).action());
        assertEquals(Action.App.create_user, new ContactUserCreateActionHandler().handle(contact, context).action());
        assertEquals(4, ContactUserCreate.class.getRecordComponents().length);
    }

    @Test
    void updateDtoRequiresEditUserAction() {
        User user = User.builder().name("Updated User").build();
        user.setId("user-2");
        UserAuthorizationUpdate dto = new UserAuthorizationUpdate(
                user, Set.of(RelationShip.App.user_manager), Set.of("lab-1")
        );

        var command = new UserUpdateActionHandler().handle(dto, context);

        assertEquals(Action.App.edit_user, command.action());
        assertEquals("operator", command.subjectId());
    }

    @Test
    void listDtoRequiresGlobalListUserAction() {
        var command = new UserListActionHandler().handle(new UserListQuery("张三"), context);

        assertEquals(Action.App.list_user, command.action());
        assertEquals("global", command.entityId());
        assertEquals("operator", command.subjectId());
    }

    @Test
    void deleteDtoRequiresDeleteUserAction() {
        var command = new UserDeleteActionHandler().handle(
                new UserDelete("user-2", "张三"), context
        );

        assertEquals(Action.App.delete_user, command.action());
        assertEquals("global", command.entityId());
        assertEquals("operator", command.subjectId());
    }
}
