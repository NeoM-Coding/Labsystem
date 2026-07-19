package xyz.jasenon.lab.base.handler.authorization.laboratory;

import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.auth.SourceType;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.permission.Action;
import xyz.jasenon.lab.base.api.dto.LaboratoryCreate;
import xyz.jasenon.lab.base.api.dto.LaboratoryDelete;
import xyz.jasenon.lab.base.api.dto.LaboratoryEdit;

import java.util.Set;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LaboratoryAuthorizationActionHandlerTests {

    private final UserContext context = UserContext.of(
            "operator", "operator", "Operator", Set.of("lab-1"), Set.of()
    );

    @Test
    void allLaboratoryMutationsRequireGlobalManageLaboratoryAction() {
        var create = new LaboratoryCreateActionHandler().handle(
                new LaboratoryCreate("16号楼", "计算机学院", "实验室", null, null), context
        );
        var edit = new LaboratoryEditActionHandler().handle(
                new LaboratoryEdit("lab-1", "16号楼", "计算机学院", "实验室", null, null), context
        );
        var delete = new LaboratoryDeleteActionHandler().handle(
                new LaboratoryDelete("lab-1", "实验室"), context
        );

        for (var command : List.of(create, edit, delete)) {
            assertEquals(SourceType.app, command.entityType());
            assertEquals("global", command.entityId());
            assertEquals(Action.App.manage_laboratory, command.action());
            assertEquals("operator", command.subjectId());
        }
    }
}
