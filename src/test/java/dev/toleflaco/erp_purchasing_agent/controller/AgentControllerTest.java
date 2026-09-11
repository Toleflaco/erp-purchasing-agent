package dev.toleflaco.erp_purchasing_agent.controller;

import dev.toleflaco.erp_purchasing_agent.agent.ReActAgent;
import dev.toleflaco.erp_purchasing_agent.exception.GuardrailExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentController.class)
public class AgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReActAgent agent;

    @Test
    void shouldReturn422ProblemDetailWhenGuardrailExceededIsThrown() throws Exception {
        // Given
        given(agent.run(anyString()))
                .willThrow(new GuardrailExceededException(
                        GuardrailExceededException.GuardrailType.ITERATIONS, 4L, 4L
                ));
        // When + Then
        mockMvc.perform(post("/agent/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"cualquier cosa\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.title").value("Guardrail exceeded"))
                .andExpect(jsonPath("$.guardrailType").value("ITERATIONS"))
                .andExpect(jsonPath("$.value").value(4))
                .andExpect(jsonPath("$.limit").value(4));
    }

}
