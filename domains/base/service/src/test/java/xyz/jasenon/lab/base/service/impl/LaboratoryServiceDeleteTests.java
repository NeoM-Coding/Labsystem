package xyz.jasenon.lab.base.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.context.UserContextHolder;
import xyz.jasenon.lab.auth.context.UserContextStore;
import xyz.jasenon.lab.auth.service.LaboratoryAuthorization;
import xyz.jasenon.lab.base.api.dto.LaboratoryDelete;
import xyz.jasenon.lab.base.mapper.LaboratoryMapper;
import xyz.jasenon.lab.base.mapper.UserMapper;
import xyz.jasenon.lab.common.error.ErrorCode;
import xyz.jasenon.lab.common.exception.BusinessException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LaboratoryServiceDeleteTests {

    private final LaboratoryMapper mapper = mock(LaboratoryMapper.class);
    private final LaboratoryAuthorization authorization = mock(LaboratoryAuthorization.class);
    private final LaboratoryServiceImpl service = new LaboratoryServiceImpl(
            authorization, mock(UserMapper.class), mock(UserContextStore.class));
    private final LaboratoryDelete command = new LaboratoryDelete("lab-1", null);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        UserContextHolder.set(UserContext.of("user-1", "operator", "Operator", List.of()));
        when(mapper.lockActiveLaboratory("lab-1")).thenReturn("lab-1");
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void blocksDeletionWhenDeviceStillBelongsToLaboratory() {
        when(mapper.hasActiveDevices("lab-1")).thenReturn(true);

        assertBlocked(ErrorCode.LABORATORY_HAS_DEVICES);
    }

    @Test
    void blocksDeletionWhenGatewayStillUsesLaboratory() {
        when(mapper.hasActiveGateways("lab-1")).thenReturn(true);

        assertBlocked(ErrorCode.LABORATORY_HAS_GATEWAYS);
    }

    @Test
    void deletesLaboratoryWithNoActiveBindings() {
        service.delete(command);

        verify(mapper).deleteById("lab-1");
        verify(authorization).remove("lab-1");
    }

    private void assertBlocked(ErrorCode error) {
        assertThatThrownBy(() -> service.delete(command))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(error.code()));
        verify(mapper, never()).deleteById("lab-1");
        verifyNoInteractions(authorization);
    }
}
