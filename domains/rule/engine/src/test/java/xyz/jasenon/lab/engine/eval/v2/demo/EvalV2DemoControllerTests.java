package xyz.jasenon.lab.engine.eval.v2.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EvalV2DemoController.class)
@Import(EvalV2DemoForest.class)
class EvalV2DemoControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesTheRealForestAndAcceptsEvents() throws Exception {
        mockMvc.perform(get("/api/eval-v2-demo/forest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics.eventSources").value(5))
                .andExpect(jsonPath("$.metrics.predicates").value(12))
                .andExpect(jsonPath("$.metrics.trees").value(4));

        mockMvc.perform(post("/api/eval-v2-demo/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"field":"roomTemperature","value":"32"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changedRoots").isEmpty());

        mockMvc.perform(post("/api/eval-v2-demo/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"field":"errorCode","value":"1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changedRoots.outside-comfort").value(true))
                .andExpect(jsonPath("$.forest.roots.outside-comfort").value(true));
    }
}
