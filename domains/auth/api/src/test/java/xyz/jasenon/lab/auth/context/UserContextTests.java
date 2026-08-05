package xyz.jasenon.lab.auth.context;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserContextTests {

    private final UserContext context = UserContext.of(
            "user-1",
            "jasenon",
            "Jasenon",
            List.of(
                    scope("lab-1", "16号楼", "计算机科学学院"),
                    scope("lab-2", "16号楼", "人工智能学院"),
                    scope("lab-3", "18号楼", "计算机科学学院")
            )
    );

    @Test
    void noFilterReturnsTheCompleteViewScope() {
        assertEquals(List.of("lab-1", "lab-2", "lab-3"), context.filterLaboratoryIds());
    }

    @Test
    void supportsSingleDimensionFilters() {
        assertEquals(
                List.of("lab-1", "lab-2"),
                context.filterLaboratoryIdsByBuildingName("16号楼")
        );
        assertEquals(
                List.of("lab-1", "lab-3"),
                context.filterLaboratoryIdsByOrgName("计算机科学学院")
        );
    }

    @Test
    void supportsCombinedBuildingAndOrganizationFilter() {
        assertEquals(
                List.of("lab-1"),
                context.filterLaboratoryIds("16号楼", "计算机科学学院")
        );
    }

    @Test
    void supportsMultipleValuesWithinEachFilterDimension() {
        assertEquals(
                List.of("lab-1", "lab-2", "lab-3"),
                context.filterLaboratoryIds(
                        new String[]{"16号楼", "18号楼"},
                        new String[]{"计算机科学学院", "人工智能学院"}
                )
        );
        assertEquals(
                List.of("lab-1", "lab-3"),
                context.filterLaboratoryIds(
                        new String[]{"16号楼", "18号楼"},
                        new String[]{"计算机科学学院"}
                )
        );
    }

    @Test
    void ignoresBlankAndDuplicateFilterValues() {
        assertEquals(
                List.of("lab-1", "lab-2"),
                context.filterLaboratoryIds(
                        new String[]{" 16号楼 ", "", "16号楼"},
                        null
                )
        );
        assertEquals(
                List.of("lab-1", "lab-2", "lab-3"),
                context.filterLaboratoryIds(new String[]{" "}, new String[0])
        );
    }

    @Test
    void exposesDistinctFilterOptions() {
        assertEquals(List.of("16号楼", "18号楼"), context.getBuildingNames());
        assertEquals(List.of("计算机科学学院", "人工智能学院"), context.getOrgNames());
    }

    private static UserContext.LaboratoryScope scope(String id, String buildingName, String orgName) {
        return UserContext.LaboratoryScope.builder()
                .laboratoryId(id)
                .laboratoryName(id)
                .buildingName(buildingName)
                .orgName(orgName)
                .build();
    }
}
