package xyz.jasenon.lab.base.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import xyz.jasenon.lab.common.model.base.Laboratory;
import xyz.jasenon.lab.common.model.base.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class UserContext {

    private String userId;

    private User user;

    /**
     * Laboratory view scope. It is derived from laboratoryScopes and can be used
     * directly as the visible laboratory id set.
     */
    @Builder.Default
    private List<String> laboratoryIds = new ArrayList<>();

    /**
     * Visible laboratory dimension rows. These rows support filters such as
     * buildingName + orgName -> laboratoryIds inside the user's view scope.
     */
    @Builder.Default
    private List<LaboratoryScope> laboratoryScopes = new ArrayList<>();

    private LocalDateTime loginAt;

    public static UserContext of(User user, Collection<Laboratory> visibleLaboratories) {
        List<LaboratoryScope> scopes = toScopes(visibleLaboratories);
        return UserContext.builder()
                .userId(user == null ? null : user.getId())
                .user(mask(user))
                .laboratoryIds(scopes.stream().map(LaboratoryScope::getLaboratoryId).toList())
                .laboratoryScopes(scopes)
                .loginAt(LocalDateTime.now())
                .build();
    }

    public static UserContext of(User user,
                                 Collection<String> laboratoryIds,
                                 Collection<LaboratoryScope> laboratoryScopes) {
        List<LaboratoryScope> scopes = copyScopes(laboratoryScopes);
        return UserContext.builder()
                .userId(user == null ? null : user.getId())
                .user(mask(user))
                .laboratoryIds(copyDistinct(laboratoryIds))
                .laboratoryScopes(scopes)
                .loginAt(LocalDateTime.now())
                .build();
    }

    public boolean hasLaboratoryViewScope() {
        return !laboratoryIds.isEmpty();
    }

    public boolean canViewLaboratory(String laboratoryId) {
        return contains(laboratoryIds, laboratoryId);
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
        if (filter == null) {
            return filterLaboratoryIds();
        }
        return filterLaboratoryIds(filter.getBuildingName(), filter.getOrgName());
    }

    public List<String> getBuildingNames() {
        return distinctScopeValues(LaboratoryScope::getBuildingName);
    }

    public List<String> getOrgNames() {
        return distinctScopeValues(LaboratoryScope::getOrgName);
    }

    private static User mask(User user) {
        return user == null ? null : user.mask();
    }

    private static List<LaboratoryScope> toScopes(Collection<Laboratory> laboratories) {
        if (laboratories == null || laboratories.isEmpty()) {
            return new ArrayList<>();
        }
        return laboratories.stream()
                .filter(Objects::nonNull)
                .map(LaboratoryScope::from)
                .filter(scope -> !isBlank(scope.getLaboratoryId()))
                .distinct()
                .toList();
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

    private static boolean contains(Collection<String> values, String value) {
        return !isBlank(value) && values != null && values.contains(value.trim());
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private List<String> distinctScopeValues(ScopeValueReader reader) {
        List<LaboratoryScope> scopes = scopeSnapshot();
        if (scopes.isEmpty()) {
            return new ArrayList<>();
        }
        return scopes.stream()
                .map(reader::read)
                .filter(value -> !isBlank(value))
                .map(String::trim)
                .distinct()
                .toList();
    }

    private List<LaboratoryScope> scopeSnapshot() {
        return laboratoryScopes == null ? new ArrayList<>() : laboratoryScopes;
    }

    @FunctionalInterface
    private interface ScopeValueReader {
        String read(LaboratoryScope scope);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LaboratoryScope {

        private String laboratoryId;

        private String laboratoryName;

        private String buildingName;

        private String orgName;

        public static LaboratoryScope from(Laboratory laboratory) {
            return LaboratoryScope.builder()
                    .laboratoryId(laboratory.getId())
                    .laboratoryName(laboratory.getLaboratoryName())
                    .buildingName(laboratory.getBuildingName())
                    .orgName(laboratory.getOrgName())
                    .build();
        }

        public boolean matches(String buildingName, String orgName) {
            return matchesFilter(this.buildingName, buildingName)
                    && matchesFilter(this.orgName, orgName);
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LaboratoryFilter {

        private String buildingName;

        private String orgName;
    }

    private static boolean matchesFilter(String actual, String expected) {
        return isBlank(expected) || Objects.equals(trimToEmpty(actual), expected.trim());
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
