package xyz.jasenon.lab.auth.client;

import co.permify.sdk.api.DataApi;
import co.permify.sdk.api.PermissionApi;
import co.permify.sdk.api.SchemaApi;
import co.permify.sdk.client.ApiClient;
import co.permify.sdk.client.ApiException;
import co.permify.sdk.model.*;
import lombok.extern.slf4j.Slf4j;
import xyz.jasenon.lab.auth.SourceType;
import xyz.jasenon.lab.auth.config.PermifyAuthProperties;
import xyz.jasenon.lab.auth.permission.Permission;
import xyz.jasenon.lab.auth.permission.RelationShip;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

@Slf4j
public class AuthClient implements AuthorizationOperations {

    private final ApiClient client;
    private final String tenantId;
    private final int depth;
    private final long lookupPageSize;
    private volatile String schemaVersion;

    public AuthClient(PermifyAuthProperties properties) {
        ApiClient client = new ApiClient();
        client.updateBaseUri(properties.getBaseUrl());
        this.client = client;
        this.tenantId = properties.getTenantId();
        this.depth = properties.getDepth();
        this.lookupPageSize = properties.getLookupPageSize();
        this.schemaVersion = normalize(properties.getSchemaVersion());
        log.info("AuthClient 初始化完毕 url:{} tenant:{}", properties.getBaseUrl(), tenantId);
    }

    public synchronized void updateSchemaVersion() {
        SchemaApi api = new SchemaApi(client);
        var body = new SchemaListBody();
        try {
            var resp = api.schemasList(tenantId, body);
            if (resp.getSchemas() != null &&  !resp.getSchemas().isEmpty()){
                schemaVersion = resp.getSchemas().get(0).getVersion();
                return;
            }
            throw new IllegalStateException("Permify 中尚未上传授权模型");
        } catch (ApiException e) {
            throw new IllegalStateException("Permify schema 版本号同步失败", e);
        }
    }

    @Override
    public boolean grant(SourceType source, String sourceId, RelationShip relationShip, SourceType target, String targetId){
        DataApi api = new DataApi(client);
        var body = new WriteRelationshipsBody()
                .metadata(new RelationshipWriteRequestMetadata().schemaVersion(requireSchemaVersion()))
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

    @Override
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

    @Override
    public boolean check(SourceType source, String sourceId, Permission permission, SourceType target, String targetId){
        PermissionApi api = new PermissionApi(client);
        var body = new CheckBody()
                .entity(entity(source,sourceId))
                .subject(subject(target,targetId))
                .permission(permission.str())
                .metadata(new PermissionCheckRequestMetadata()
                        .schemaVersion(requireSchemaVersion())
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

    @Override
    public Set<String> relationsOf(SourceType source, String sourceId, SourceType target, String targetId) {
        DataApi api = new DataApi(client);
        Set<String> relations = new LinkedHashSet<>();
        String continuousToken = "";
        try {
            do {
                var body = new ReadRelationshipsBody()
                        .filter(new TupleFilter()
                                .entity(new EntityFilter().type(source.name()).ids(List.of(sourceId)))
                                .subject(new SubjectFilter().type(target.name()).ids(List.of(targetId))))
                        .pageSize(100L)
                        .continuousToken(continuousToken);
                var response = api.dataRelationshipsRead(tenantId, body);
                if (response.getTuples() != null) {
                    response.getTuples().stream()
                            .map(Tuple::getRelation)
                            .filter(AuthClient::hasText)
                            .forEach(relations::add);
                }
                continuousToken = normalize(response.getContinuousToken());
            } while (!continuousToken.isEmpty());
            return Set.copyOf(relations);
        } catch (ApiException e) {
            log.error("read relationships error args:{} {} {} {}", source, sourceId, target, targetId, e);
            throw new IllegalStateException("查询用户关系失败", e);
        }
    }

    @Override
    public Set<String> entityIdsOf(SourceType source, RelationShip relationShip,
                                   SourceType target, String targetId) {
        DataApi api = new DataApi(client);
        Set<String> entityIds = new LinkedHashSet<>();
        String continuousToken = "";
        try {
            do {
                var body = new ReadRelationshipsBody()
                        .filter(new TupleFilter()
                                .entity(new EntityFilter().type(source.name()))
                                .relation(relationShip.str())
                                .subject(new SubjectFilter().type(target.name()).ids(List.of(targetId))))
                        .pageSize(100L)
                        .continuousToken(continuousToken);
                var response = api.dataRelationshipsRead(tenantId, body);
                if (response.getTuples() != null) {
                    response.getTuples().stream()
                            .map(Tuple::getEntity)
                            .filter(java.util.Objects::nonNull)
                            .map(Entity::getId)
                            .filter(AuthClient::hasText)
                            .forEach(entityIds::add);
                }
                continuousToken = normalize(response.getContinuousToken());
            } while (!continuousToken.isEmpty());
            return Set.copyOf(entityIds);
        } catch (ApiException e) {
            log.error("read relationship entities error args:{} {} {} {}",
                    source, relationShip, target, targetId, e);
            throw new IllegalStateException("查询用户资源关系失败", e);
        }
    }

    @Override
    public Set<String> lookupEntityIds(SourceType entityType, Permission permission,
                                       SourceType subjectType, String subjectId) {
        // Permission lookup 会展开 DSL 中的继承路径，例如 super_admin -> laboratory.can_view。
        PermissionApi api = new PermissionApi(client);
        Set<String> entityIds = new LinkedHashSet<>();
        String continuousToken = "";
        try {
            do {
                // Permify 使用 continuous token 分页，必须遍历到空 token 才是完整 view scope。
                var body = new LookupEntityBody()
                        .metadata(new PermissionLookupEntityRequestMetadata()
                                .schemaVersion(requireSchemaVersion())
                                .depth(depth))
                        .entityType(entityType.name())
                        .permission(permission.str())
                        .subject(subject(subjectType, subjectId))
                        .pageSize(lookupPageSize)
                        .continuousToken(continuousToken);
                var response = api.permissionsLookupEntity(tenantId, body);
                if (response.getEntityIds() != null) {
                    response.getEntityIds().stream()
                            .filter(AuthClient::hasText)
                            .forEach(entityIds::add);
                }
                continuousToken = normalize(response.getContinuousToken());
            } while (!continuousToken.isEmpty());
            return Set.copyOf(entityIds);
        } catch (ApiException e) {
            log.error("lookup permission entities error args:{} {} {} {}",
                    entityType, permission, subjectType, subjectId, e);
            throw new IllegalStateException("反向查询用户可访问资源失败", e);
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

    private String requireSchemaVersion() {
        if (schemaVersion.isEmpty()) {
            updateSchemaVersion();
        }
        return schemaVersion;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
