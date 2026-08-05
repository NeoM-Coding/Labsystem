package xyz.jasenon.lab.auth.service;

import java.util.Set;

/**
 * Laboratory 领域与授权图之间的协作端口，不承载通用 grant/revoke 语义。
 */
public interface LaboratoryAuthorization {

    void initialize(String laboratoryId, String creatorUserId);

    void remove(String laboratoryId);

    Set<String> visibleLaboratoryIds(String userId);

    Set<String> usersWhoCanView(String laboratoryId);
}
