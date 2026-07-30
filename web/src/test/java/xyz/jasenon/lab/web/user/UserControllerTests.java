package xyz.jasenon.lab.web.user;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.jasenon.lab.base.api.dto.UserListQuery;
import xyz.jasenon.lab.base.api.model.User;
import xyz.jasenon.lab.base.api.service.UserService;
import xyz.jasenon.lab.common.rpc.RpcResult;

import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserControllerTests {

    @Test
    void listAdaptsKeywordToSerializableRpcQuery() {
        UserService userService = mock(UserService.class);
        when(userService.list(org.mockito.ArgumentMatchers.any()))
                .thenReturn(RpcResult.success(List.of()));
        UserController controller = new UserController();
        ReflectionTestUtils.setField(controller, "userService", userService);

        controller.list(" 张三 ");

        verify(userService).list(argThat((UserListQuery query) ->
                " 张三 ".equals(query.keyword())
        ));
    }

    @Test
    void listReturnsUsersProvidedByRpcBoundary() {
        UserService userService = mock(UserService.class);
        User user = User.builder().name("张三").username("zhangsan").password("").build();
        user.setId("user-1");
        when(userService.list(org.mockito.ArgumentMatchers.any()))
                .thenReturn(RpcResult.success(List.of(user)));
        UserController controller = new UserController();
        ReflectionTestUtils.setField(controller, "userService", userService);

        controller.list(null);

        verify(userService).list(new UserListQuery(null));
    }
}
