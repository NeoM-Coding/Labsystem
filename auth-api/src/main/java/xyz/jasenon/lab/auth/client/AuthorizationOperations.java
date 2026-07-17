package xyz.jasenon.lab.auth.client;

import xyz.jasenon.lab.auth.SourceType;
import xyz.jasenon.lab.auth.permission.Permission;
import xyz.jasenon.lab.auth.permission.RelationShip;

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

    boolean check(SourceType source,
                  String sourceId,
                  Permission permission,
                  SourceType target,
                  String targetId);
}
