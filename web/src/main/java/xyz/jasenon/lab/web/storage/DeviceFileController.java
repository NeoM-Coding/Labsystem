package xyz.jasenon.lab.web.storage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import xyz.jasenon.lab.storage.*;
import xyz.jasenon.lab.auth.SourceType;
import xyz.jasenon.lab.auth.client.AuthorizationOperations;
import xyz.jasenon.lab.auth.context.UserContextHolder;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.common.util.R;
import xyz.jasenon.lab.web.response.DiyResponseEntity;
import java.io.InputStream;
import java.util.*;

@RestController
@RequestMapping("/api/device-files")
@ConditionalOnProperty(name="lab.storage.enabled",havingValue="true")
public class DeviceFileController {
    private final ObjectStorage storage;
    private final StorageProperties properties;
    private final AuthorizationOperations authorization;
    public DeviceFileController(ObjectStorage storage,StorageProperties properties,AuthorizationOperations authorization) {
        this.storage=storage;this.properties=properties;this.authorization=authorization;
    }
    private void requireAdmin() {
        var user=UserContextHolder.get();
        if(user==null || user.getUserId()==null) throw new BusinessException(401,"请先登录");
        if(!authorization.relationsOf(SourceType.app,"global",SourceType.user,user.getUserId()).contains("super_admin"))
            throw new BusinessException(403,"仅系统管理员可管理下位机文件");
    }
    @PostMapping(consumes="multipart/form-data")
    public DiyResponseEntity<R<Map<String,Object>>> upload(@RequestParam MultipartFile file) throws Exception {
        requireAdmin();
        if(file.isEmpty() || file.getSize()>20*1024*1024) throw new BusinessException(400,"文件大小需为 1 字节至 20 MB");
        String key="device-files/"+UUID.randomUUID();
        try(InputStream input=file.getInputStream()) {
            storage.put(properties.deviceBucket(),key,input,file.getSize(),"application/octet-stream");
        }
        return DiyResponseEntity.of(R.success(Map.of("key",key,"size",file.getSize())));
    }
    @GetMapping("/download-url")
    public DiyResponseEntity<R<Map<String,Object>>> url(@RequestParam String key,@RequestParam(defaultValue="300") int expiresSeconds) throws Exception {
        requireAdmin();
        if(!key.matches("device-files/[a-f0-9-]{36}") || expiresSeconds<1 || expiresSeconds>3600)
            throw new BusinessException(400,"文件标识或有效期无效");
        return DiyResponseEntity.of(R.success(Map.of("url",storage.downloadUrl(properties.deviceBucket(),key,expiresSeconds),"expiresSeconds",expiresSeconds)));
    }
}
