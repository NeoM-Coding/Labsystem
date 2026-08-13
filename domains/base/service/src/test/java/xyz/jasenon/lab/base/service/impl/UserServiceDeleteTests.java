package xyz.jasenon.lab.base.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.context.UserContextHolder;
import xyz.jasenon.lab.auth.context.UserContextStore;
import xyz.jasenon.lab.auth.service.Auth;
import xyz.jasenon.lab.auth.service.LaboratoryAuthorization;
import xyz.jasenon.lab.base.api.dto.UserDelete;
import xyz.jasenon.lab.base.api.model.User;
import xyz.jasenon.lab.base.mapper.LaboratoryMapper;
import xyz.jasenon.lab.base.mapper.UserMapper;
import xyz.jasenon.lab.common.exception.BusinessException;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceDeleteTests {

    private UserMapper userMapper;
    private Auth auth;
    private UserContextStore contextStore;
    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        auth = mock(Auth.class);
        contextStore = mock(UserContextStore.class);
        service = new UserServiceImpl(
                auth,
                mock(LaboratoryAuthorization.class),
                mock(LaboratoryMapper.class),
                contextStore
        );
        ReflectionTestUtils.setField(service, "baseMapper", userMapper);
        UserContextHolder.set(UserContext.of(
                "operator", "operator", "Operator", Set.of()
        ));
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void deletesTargetAndCleansAuthorizationAndSession() {
        User target = User.builder().name("张三").build();
        target.setId("user-2");
        when(userMapper.selectById("user-2")).thenReturn(target);
        when(userMapper.deleteById("user-2")).thenReturn(1);

        service.deleteUser(new UserDelete(" user-2 ", "张三"));

        verify(auth).removeUser("user-2");
        verify(contextStore).delete("user-2");
    }

    @Test
    void rejectsDeletingCurrentUserBeforeMutation() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.deleteUser(new UserDelete("operator", null)));

        assertEquals(400, error.getCode());
        verify(auth, never()).removeUser("operator");
    }
}
