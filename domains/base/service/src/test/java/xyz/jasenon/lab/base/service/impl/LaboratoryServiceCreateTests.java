package xyz.jasenon.lab.base.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.context.UserContextHolder;
import xyz.jasenon.lab.auth.context.UserContextStore;
import xyz.jasenon.lab.auth.service.LaboratoryAuthorization;
import xyz.jasenon.lab.base.api.dto.LaboratoryCreate;
import xyz.jasenon.lab.base.api.model.Laboratory;
import xyz.jasenon.lab.base.mapper.LaboratoryMapper;
import xyz.jasenon.lab.base.mapper.UserMapper;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LaboratoryServiceCreateTests {

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void createPersistsCurrentUserAsCreatorAndInitializesOwner() {
        LaboratoryAuthorization authorization = mock(LaboratoryAuthorization.class);
        LaboratoryMapper mapper = mock(LaboratoryMapper.class);
        UserContextHolder.set(UserContext.of(
                "creator-1", "creator", "Creator", Set.of(), Set.of()));
        when(mapper.insert(any(Laboratory.class))).thenAnswer(invocation -> {
            Laboratory laboratory = invocation.getArgument(0);
            laboratory.setId("lab-1");
            return 1;
        });
        when(authorization.usersWhoCanView("lab-1")).thenReturn(Set.of("creator-1"));

        LaboratoryServiceImpl service = new LaboratoryServiceImpl(
                authorization, mock(UserMapper.class), mock(UserContextStore.class));
        ReflectionTestUtils.setField(service, "baseMapper", mapper);

        Laboratory created = service.create(new LaboratoryCreate(
                "创新楼", "计算机学院", "软件实验室", null, null)).data();

        assertThat(created.getCreateBy()).isEqualTo("creator-1");
        verify(authorization).reconcile("lab-1", "creator-1", Set.of());
    }
}
