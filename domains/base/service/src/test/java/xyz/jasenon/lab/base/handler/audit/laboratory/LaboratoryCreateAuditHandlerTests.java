package xyz.jasenon.lab.base.handler.audit.laboratory;

import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.base.api.dto.LaboratoryCreate;
import xyz.jasenon.lab.base.api.model.Laboratory;

import static org.assertj.core.api.Assertions.assertThat;

class LaboratoryCreateAuditHandlerTests {

    @Test
    void usesCreatedLaboratoryIdFromMethodResult() {
        Laboratory result = new Laboratory();
        result.setId("lab-42");

        var fragment = new LaboratoryCreateAuditHandler()
                .handle(new LaboratoryCreate("16号楼", "计算机科学学院", "软件实验室", null, null), result);

        assertThat(fragment.objectId()).isEqualTo("lab-42");
        assertThat(fragment.description()).isEqualTo("创建实验室「软件实验室」");
    }
}
