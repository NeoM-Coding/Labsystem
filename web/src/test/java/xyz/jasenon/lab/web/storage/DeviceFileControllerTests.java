package xyz.jasenon.lab.web.storage;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import xyz.jasenon.lab.auth.SourceType;
import xyz.jasenon.lab.auth.client.AuthorizationOperations;
import xyz.jasenon.lab.auth.context.*;
import xyz.jasenon.lab.storage.*;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class DeviceFileControllerTests {
    @AfterEach void clear() { UserContextHolder.clear(); }
    @Test void onlyAdminCanUploadAndOnlyDeviceKeysCanBeSigned() throws Exception {
        var auth=mock(AuthorizationOperations.class);var storage=mock(ObjectStorage.class);
        var controller=new DeviceFileController(storage,new StorageProperties(null,null,null,null,null,null,null),auth);
        assertThatThrownBy(()->controller.url("device-files/00000000-0000-0000-0000-000000000000",30)).hasMessageContaining("登录");
        UserContextHolder.set(UserContext.builder().userId("user").build());
        when(auth.relationsOf(SourceType.app,"global",SourceType.user,"user")).thenReturn(Set.of());
        assertThatThrownBy(()->controller.upload(new MockMultipartFile("file","face.jpg","image/jpeg",new byte[]{1}))).hasMessageContaining("管理员");
        when(auth.relationsOf(SourceType.app,"global",SourceType.user,"user")).thenReturn(Set.of("super_admin"));
        assertThatThrownBy(()->controller.url("../lab-telemetry-archive/secret",30)).hasMessageContaining("无效");
        assertThatThrownBy(()->controller.url("device-files/00000000-0000-0000-0000-000000000000",3601)).hasMessageContaining("无效");
        controller.upload(new MockMultipartFile("file","face.jpg","image/jpeg",new byte[]{1,2}));
        verify(storage).put(eq("lab-device-files"),matches("device-files/[a-f0-9-]{36}"),any(),eq(2L),eq("application/octet-stream"));
        verify(storage,never()).downloadUrl(any(),any(),anyInt());
    }
}
