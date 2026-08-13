package xyz.jasenon.lab.base.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.context.UserContextHolder;
import xyz.jasenon.lab.auth.context.UserContextStore;
import xyz.jasenon.lab.auth.service.LaboratoryAuthorization;
import xyz.jasenon.lab.base.mapper.UserMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LaboratoryServiceScopeTests {

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void optionsOnlyContainCurrentUsersVisibleLaboratoryScopes() {
        UserContextHolder.set(UserContext.of(
                "user-1", "operator", "Operator", List.of(
                        UserContext.LaboratoryScope.builder()
                                .laboratoryId("lab-1")
                                .buildingName("创新楼")
                                .orgName("计算机学院")
                                .build(),
                        UserContext.LaboratoryScope.builder()
                                .laboratoryId("lab-2")
                                .buildingName("实验楼")
                                .orgName("电子学院")
                                .build()
                )
        ));
        LaboratoryServiceImpl service = new LaboratoryServiceImpl(
                mock(LaboratoryAuthorization.class),
                mock(UserMapper.class),
                mock(UserContextStore.class)
        );

        var organizations = service.collectionOrgName().data();
        var buildings = service.collectionBuildingName().data();

        assertThat(organizations).extracting(pair -> pair.f + ":" + pair.s)
                .containsExactly("lab-1:计算机学院", "lab-2:电子学院");
        assertThat(buildings).extracting(pair -> pair.f + ":" + pair.s)
                .containsExactly("lab-1:创新楼", "lab-2:实验楼");
    }
}
