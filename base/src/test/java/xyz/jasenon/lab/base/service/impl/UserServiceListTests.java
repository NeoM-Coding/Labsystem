package xyz.jasenon.lab.base.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.jasenon.lab.auth.context.UserContextStore;
import xyz.jasenon.lab.auth.service.Auth;
import xyz.jasenon.lab.auth.service.LaboratoryAuthorization;
import xyz.jasenon.lab.base.api.dto.UserListQuery;
import xyz.jasenon.lab.base.api.model.User;
import xyz.jasenon.lab.base.mapper.LaboratoryMapper;
import xyz.jasenon.lab.base.mapper.UserMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceListTests {

    @Test
    void listMasksPasswordsBeforeCrossingRpcBoundary() {
        UserMapper userMapper = mock(UserMapper.class);
        User persisted = User.builder()
                .name("张三")
                .username("zhangsan")
                .password("encoded-secret")
                .email("zhangsan@example.com")
                .build();
        persisted.setId("user-1");
        when(userMapper.listUsers("张三")).thenReturn(List.of(persisted));
        UserServiceImpl service = new UserServiceImpl(
                mock(Auth.class),
                mock(LaboratoryAuthorization.class),
                mock(LaboratoryMapper.class),
                mock(UserContextStore.class)
        );
        ReflectionTestUtils.setField(service, "baseMapper", userMapper);

        User returned = service.list(new UserListQuery("张三")).data().get(0);

        assertEquals("", returned.getPassword());
        assertEquals("encoded-secret", persisted.getPassword());
        assertEquals("user-1", returned.getId());
    }

    @Test
    void listKeepsContactsWithoutLoginNames() {
        UserMapper userMapper = mock(UserMapper.class);
        User contact = User.builder()
                .name("李老师")
                .email("li@example.com")
                .build();
        contact.setId("contact-1");
        when(userMapper.listUsers("李老师")).thenReturn(List.of(contact));
        UserServiceImpl service = new UserServiceImpl(
                mock(Auth.class),
                mock(LaboratoryAuthorization.class),
                mock(LaboratoryMapper.class),
                mock(UserContextStore.class)
        );
        ReflectionTestUtils.setField(service, "baseMapper", userMapper);

        User returned = service.list(new UserListQuery("李老师")).data().get(0);

        assertEquals("contact-1", returned.getId());
        assertEquals("李老师", returned.getName());
        assertNull(returned.getUsername());
    }
}
