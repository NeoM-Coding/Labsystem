package xyz.jasenon.lab.auth.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class UserContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String userId;

    private String username;

    private String displayName;

    @Builder.Default
    private List<String> laboratoryIds = new ArrayList<>();

    @Builder.Default
    private List<LaboratoryScope> laboratoryScopes = new ArrayList<>();

    private LocalDateTime loginAt;

    public static UserContext of(String userId,
                                 String username,
                                 String displayName,
                                 Collection<LaboratoryScope> laboratoryScopes) {
        List<LaboratoryScope> scopes = copyScopes(laboratoryScopes);
        return UserContext.builder()
                .userId(trimToNull(userId))
                .username(trimToNull(username))
                .displayName(trimToNull(displayName))
                .laboratoryIds(scopes.stream().map(LaboratoryScope::getLaboratoryId).distinct().toList())
                .laboratoryScopes(scopes)
                .loginAt(LocalDateTime.now())
                .build();
    }

    public static UserContext of(String userId,
                                 String username,
                                 String displayName,
                                 Collection<String> laboratoryIds,
                                 Collection<LaboratoryScope> laboratoryScopes) {
        return UserContext.builder()
                .userId(trimToNull(userId))
                .username(trimToNull(username))
                .displayName(trimToNull(displayName))
                .laboratoryIds(copyDistinct(laboratoryIds))
                .laboratoryScopes(copyScopes(laboratoryScopes))
                .loginAt(LocalDateTime.now())
                .build();
    }

    public boolean hasLaboratoryViewScope() {
        return laboratoryIds != null && !laboratoryIds.isEmpty();
    }

    public boolean canViewLaboratory(String laboratoryId) {
        return !isBlank(laboratoryId)
                && laboratoryIds != null
                && laboratoryIds.contains(laboratoryId.trim());
    }

    public List<String> filterLaboratoryIds() {
        return laboratoryIds == null ? new ArrayList<>() : new ArrayList<>(laboratoryIds);
    }

    public List<String> filterLaboratoryIdsByBuildingName(String buildingName) {
        return filterLaboratoryIds(buildingName, null);
    }

    public List<String> filterLaboratoryIdsByOrgName(String orgName) {
        return filterLaboratoryIds(null, orgName);
    }

    public List<String> filterLaboratoryIds(String buildingName, String orgName) {
        if (isBlank(buildingName) && isBlank(orgName)) {
            return filterLaboratoryIds();
        }
        return scopeSnapshot().stream()
                .filter(scope -> scope.matches(buildingName, orgName))
                .map(LaboratoryScope::getLaboratoryId)
                .distinct()
                .toList();
    }

    public List<String> filterLaboratoryIds(LaboratoryFilter filter) {
        return filter == null
                ? filterLaboratoryIds()
                : filterLaboratoryIds(filter.getBuildingName(), filter.getOrgName());
    }

    public List<String> getBuildingNames() {
        return distinctScopeValues(LaboratoryScope::getBuildingName);
    }

    public List<String> getOrgNames() {
        return distinctScopeValues(LaboratoryScope::getOrgName);
    }

    private List<String> distinctScopeValues(ScopeValueReader reader) {
        return scopeSnapshot().stream()
                .map(reader::read)
                .filter(value -> !isBlank(value))
                .map(String::trim)
                .distinct()
                .toList();
    }

    private List<LaboratoryScope> scopeSnapshot() {
        return laboratoryScopes == null ? List.of() : laboratoryScopes;
    }

    private static List<LaboratoryScope> copyScopes(Collection<LaboratoryScope> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return new ArrayList<>();
        }
        return scopes.stream()
                .filter(Objects::nonNull)
                .filter(scope -> !isBlank(scope.getLaboratoryId()))
                .distinct()
                .toList();
    }

    private static List<String> copyDistinct(Collection<String> source) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        return source.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    private static boolean matchesFilter(String actual, String expected) {
        return isBlank(expected) || Objects.equals(trimToEmpty(actual), expected.trim());
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    @FunctionalInterface
    private interface ScopeValueReader {
        String read(LaboratoryScope scope);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LaboratoryScope implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String laboratoryId;

        private String laboratoryName;

        private String buildingName;

        private String orgName;

        public boolean matches(String buildingName, String orgName) {
            return matchesFilter(this.buildingName, buildingName)
                    && matchesFilter(this.orgName, orgName);
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LaboratoryFilter implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String buildingName;

        private String orgName;
    }
}
