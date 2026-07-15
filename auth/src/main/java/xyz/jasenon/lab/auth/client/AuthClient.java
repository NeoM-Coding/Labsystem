package xyz.jasenon.lab.auth.client;

import co.permify.sdk.api.DataApi;
import co.permify.sdk.api.PermissionApi;
import co.permify.sdk.api.SchemaApi;
import co.permify.sdk.client.ApiClient;
import co.permify.sdk.client.ApiException;
import co.permify.sdk.client.Configuration;
import co.permify.sdk.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xyz.jasenon.lab.auth.SourceType;
import xyz.jasenon.lab.auth.permission.Permission;
import xyz.jasenon.lab.auth.permission.RelationShip;

import java.util.List;

@Slf4j
@Component
public class AuthClient {

    @Value("${permify.base-url}")
    private String baseUrl = "http://localhost:3476";
    private final String tenantId = "t1";
    private String schemaVersion = "";

    private final ApiClient client;

    public AuthClient(){
        ApiClient client = Configuration.getDefaultApiClient();
        client.updateBaseUri(baseUrl);
        this.client = client;
        log.info("AuthClient 初始化完毕 url:{}", baseUrl);
        updateSchemaVersion();
    }

    public void updateSchemaVersion(){
        SchemaApi api = new SchemaApi(client);
        var body = new SchemaListBody();
        try {
            var resp = api.schemasList(tenantId, body);
            if (resp.getSchemas() != null &&  !resp.getSchemas().isEmpty()){
                schemaVersion = resp.getSchemas().get(0).getVersion();
            }
        }catch (ApiException e){
            throw new RuntimeException("schema 版本号同步失败!");
        }
    }

    public boolean grant(SourceType source, String sourceId, RelationShip relationShip, SourceType target, String targetId){
        DataApi api = new DataApi(client);
        var body = new WriteRelationshipsBody()
                .metadata(new RelationshipWriteRequestMetadata().schemaVersion(schemaVersion))
                .tuples(List.of(AuthClient.tuple(source,sourceId,relationShip,target,targetId)));
        try {
            var resp = api.relationshipsWrite(tenantId,body);
            return true;
        }catch (ApiException e){
            log.error("ApiException:{}",e.getMessage());
            log.error("grant relationship error args:{} {} {} {} {}",
                    source, sourceId, relationShip, target, targetId
            );
            throw new RuntimeException("授权失败");
        }
    }

    public boolean revoke(SourceType source, String sourceId, RelationShip relationShip, SourceType target, String targetId){
        DataApi api = new DataApi(client);
        var body = new DeleteRelationshipsBody()
                .filter(filter(source,sourceId,relationShip,target,targetId));
        try {
            var resp = api.relationshipsDelete(tenantId, body);
            return true;
        }catch (ApiException e){
            log.error("ApiException:{}",e.getMessage());
            log.error("revoke relationship error args:{} {} {} {} {}",
                    source, sourceId, relationShip, target, targetId
            );
            throw new RuntimeException("回收权限失败");
        }
    }

    public boolean check(SourceType source, String sourceId, Permission permission, SourceType target, String targetId){
        PermissionApi api = new PermissionApi(client);
        var body = new CheckBody()
                .entity(entity(source,sourceId))
                .subject(subject(target,targetId))
                .permission(permission.str())
                .metadata(new PermissionCheckRequestMetadata()
                        .schemaVersion(schemaVersion)
                );
        try {
            var resp = api.permissionsCheck(tenantId, body);
            return resp.getCan() == CheckResult.ALLOWED;
        }catch (ApiException e){
            log.error("ApiException:{}",e.getMessage());
            log.error("check permission error args:{} {} {} {} {}",
                    source, sourceId, permission, target, targetId
            );
            throw new RuntimeException("验证权限失败");
        }
    }

    private static Tuple tuple(SourceType source, String sourceId, RelationShip relationShip, SourceType target, String targetId){
        Entity entity = new Entity().type(source.name()).id(sourceId);
        Subject subject = new Subject().type(target.name()).id(targetId);
        return new Tuple().entity(entity).relation(relationShip.str()).subject(subject);
    }

    private static TupleFilter filter(SourceType source, String sourceId, RelationShip relationShip, SourceType target, String targetId){
        EntityFilter eFilter = new EntityFilter().type(source.name()).ids(List.of(sourceId));
        SubjectFilter sFilter = new SubjectFilter().type(target.name()).ids(List.of(targetId));
        return new TupleFilter().entity(eFilter).relation(relationShip.str()).subject(sFilter);
    }

    private static Entity entity(SourceType source, String sourceId){
        return new Entity().type(source.name()).id(sourceId);
    }

    private static Subject subject(SourceType target, String targetId){
        return new Subject().type(target.name()).id(targetId);
    }

}
