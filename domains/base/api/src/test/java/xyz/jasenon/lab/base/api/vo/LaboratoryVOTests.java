package xyz.jasenon.lab.base.api.vo;

import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.base.api.model.Laboratory;
import xyz.jasenon.lab.base.api.model.User;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LaboratoryVOTests {

    @Test
    void masksManagerPasswords() {
        User manager = User.builder()
                .name("Manager")
                .username("manager")
                .password("encoded-password")
                .build();
        manager.setId("user-1");
        Laboratory laboratory = new Laboratory();
        laboratory.setId("lab-1");
        laboratory.setLaboratoryName("网络实验室");
        laboratory.setManager(List.of(manager));

        LaboratoryVO result = LaboratoryVO.from(laboratory);

        assertEquals("lab-1", result.id());
        assertEquals("", result.managers().get(0).getPassword());
        assertEquals("user-1", result.managers().get(0).getId());
    }
}
