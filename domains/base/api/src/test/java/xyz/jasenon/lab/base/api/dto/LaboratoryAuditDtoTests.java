package xyz.jasenon.lab.base.api.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LaboratoryAuditDtoTests {

    @Test
    void exposesHumanReadableLogsAndConcreteEventTypes() {
        LaboratoryCreate create = new LaboratoryCreate("16号楼", "计算机学院", "软件实验室", null, null);
        LaboratoryEdit edit = new LaboratoryEdit("lab-1", "16号楼", "计算机学院", "软件工程实验室", null, null);
        LaboratoryDelete delete = new LaboratoryDelete("lab-1", "软件工程实验室");

        assertThat(create.log()).isEqualTo("创建实验室「软件实验室」");
        assertThat(edit.log()).isEqualTo("编辑实验室「软件工程实验室」");
        assertThat(delete.log()).isEqualTo("删除实验室「软件工程实验室」");
        assertThat(create.eventType()).isEqualTo(LaboratoryCreate.class);
    }
}
