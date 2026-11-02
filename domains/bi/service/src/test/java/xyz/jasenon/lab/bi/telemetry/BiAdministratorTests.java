package xyz.jasenon.lab.bi.telemetry;
import org.junit.jupiter.api.*;
import xyz.jasenon.lab.auth.SourceType;
import xyz.jasenon.lab.auth.client.AuthorizationOperations;
import xyz.jasenon.lab.auth.context.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class BiAdministratorTests {
    @AfterEach void clear() { UserContextHolder.clear(); }
    @Test void requiresAuthenticatedSuperAdminRelation() {
        var operations=mock(AuthorizationOperations.class);var guard=new BiAdministrator(operations);
        assertThatThrownBy(guard::require).hasMessageContaining("登录");
        UserContextHolder.set(UserContext.builder().userId("analyst").build());
        when(operations.relationsOf(SourceType.app,"global",SourceType.user,"analyst")).thenReturn(Set.of("air_condition_data_analyst"));
        assertThatThrownBy(guard::require).hasMessageContaining("管理员");
        when(operations.relationsOf(SourceType.app,"global",SourceType.user,"analyst")).thenReturn(Set.of("super_admin"));
        assertThatCode(guard::require).doesNotThrowAnyException();
    }
}
