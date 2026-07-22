package xyz.jasenon.lab.auth.client;

import xyz.jasenon.lab.auth.SourceType;
import xyz.jasenon.lab.auth.permission.Permission;
import xyz.jasenon.lab.auth.permission.RelationShip;

import java.util.Set;

public interface AuthorizationOperations {

    boolean grant(SourceType source,
                  String sourceId,
                  RelationShip relationShip,
                  SourceType target,
                  String targetId);

    boolean revoke(SourceType source,
                   String sourceId,
                   RelationShip relationShip,
                   SourceType target,
                   String targetId);

    boolean deleteEntityData(SourceType source, String sourceId);

    boolean check(SourceType source,
                  String sourceId,
                  Permission permission,
                  SourceType target,
                  String targetId);

    Set<String> relationsOf(SourceType source,
                            String sourceId,
                            SourceType target,
                            String targetId);

    Set<String> entityIdsOf(SourceType source,
                            RelationShip relationShip,
                            SourceType target,
                            String targetId);

    Set<String> lookupEntityIds(SourceType entityType,
                                Permission permission,
                                SourceType subjectType,
                                String subjectId);

    Set<String> lookupSubjectIds(SourceType entityType,
                                 String entityId,
                                 Permission permission,
                                 SourceType subjectType);
}
