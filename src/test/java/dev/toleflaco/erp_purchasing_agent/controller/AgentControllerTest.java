package dev.toleflaco.erp_purchasing_agent.controller;

import dev.toleflaco.erp_purchasing_agent.agent.AgentRunResult;
import dev.toleflaco.erp_purchasing_agent.agent.ReActAgent;
import dev.toleflaco.erp_purchasing_agent.exception.GuardrailExceededException;
import dev.toleflaco.erp_purchasing_agent.exception.RunSessionNotFoundException;
import dev.toleflaco.erp_purchasing_agent.hitl.PendingToolCall;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
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


    @Test
    void shouldReturn202AcceptedWhenAgentPausesForApproval() throws Exception {
        // Given
        given(agent.run(anyString()))
                .willReturn(new AgentRunResult.Paused(
                        "run-abc",
                        "cualquier cosa",
                        List.of(new PendingToolCall("sendPurchaseOrder", "{\"purchaseOrderId\":42}")),
                        1L,
                        160L,
                        1232L,
                        0.0023
                ));
        // When + Then
        mockMvc.perform(post("/agent/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"cualquier cosa\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.run_id").value("run-abc"))
                .andExpect(jsonPath("$.original_prompt").value("cualquier cosa"))
                .andExpect(jsonPath("$.pending_tool_calls.length()").value(1))
                .andExpect(jsonPath("$.pending_tool_calls[0].name").value("sendPurchaseOrder"))
                .andExpect(jsonPath("$.pending_tool_calls[0].arguments").value("{\"purchaseOrderId\":42}"))
                .andExpect(jsonPath("$.iterations").value(1))
                .andExpect(jsonPath("$.tokens_total").value(160))
                .andExpect(jsonPath("$.duration_ms").value(1232))
                .andExpect(jsonPath("$.cost_usd").value(0.0023));

    }

    @Test
    void shouldReturn200OkWhenApprovalResumesAndCompletes() throws Exception {
        // Given
        given(agent.resume(anyString()))
                .willReturn(new AgentRunResult.Completed(
                        "orden aprobada y procesada",
                        1,
                        160,
                        1232,
                        0.0023
                ));
        // When + Then
        mockMvc.perform(post("/agent/run/run-abc/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("orden aprobada y procesada"))
                .andExpect(jsonPath("$.iterations").value(1))
                .andExpect(jsonPath("$.tokens_total").value(160))
                .andExpect(jsonPath("$.duration_ms").value(1232))
                .andExpect(jsonPath("$.cost_usd").value(0.0023));

    }

    @Test
    void shouldReturn410GoneWhenRunSessionNotFound() throws Exception {
        // Given
        given(agent.resume(anyString()))
                .willThrow(new RunSessionNotFoundException("run-missing"));

        // When + Then
        mockMvc.perform(post("/agent/run/run-missing/approve"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410))
                .andExpect(jsonPath("$.title").value("Run session not found"))
                .andExpect(jsonPath("$.run_id").value("run-missing"));

    }


}
