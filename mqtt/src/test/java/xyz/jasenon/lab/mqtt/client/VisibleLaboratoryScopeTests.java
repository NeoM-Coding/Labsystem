package xyz.jasenon.lab.mqtt.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.context.UserContextHolder;
import xyz.jasenon.lab.common.exception.BusinessException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VisibleLaboratoryScopeTests {

    private final VisibleLaboratoryScope scope = new VisibleLaboratoryScope();

    @AfterEach
    void clearContext() {
        UserContextHolder.clear();
    }

    @Test
    void intersectsRequestedIdsWithTheAuthenticatedVisibleScope() {
        UserContextHolder.set(UserContext.of(
                "user-1", "tester", "测试用户",
                List.of("lab-1", "lab-2"), List.of()
        ));

        assertEquals(List.of("lab-2"), scope.resolve(List.of("lab-2", "lab-3", "lab-2")));
    }

    @Test
    void returnsTheWholeVisibleScopeWhenTheRequestDoesNotNarrowIt() {
        UserContextHolder.set(UserContext.of(
                "user-1", "tester", "测试用户",
                List.of("lab-1", "lab-2"), List.of()
        ));

        assertEquals(List.of("lab-1", "lab-2"), scope.resolve(List.of()));
    }

    @Test
    void rejectsCallsWithoutAnAuthenticatedContext() {
        assertThrows(BusinessException.class, () -> scope.resolve(List.of("lab-1")));
    }
}
