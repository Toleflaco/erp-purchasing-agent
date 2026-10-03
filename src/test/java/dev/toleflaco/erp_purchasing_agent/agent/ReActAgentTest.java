package dev.toleflaco.erp_purchasing_agent.agent;

import dev.toleflaco.erp_purchasing_agent.config.AgentGuardrailsProperties;
import dev.toleflaco.erp_purchasing_agent.config.LlmPricingProperties;
import dev.toleflaco.erp_purchasing_agent.exception.GuardrailExceededException;
import dev.toleflaco.erp_purchasing_agent.hitl.AgentMessage;
import dev.toleflaco.erp_purchasing_agent.hitl.AgentRunSession;
import dev.toleflaco.erp_purchasing_agent.hitl.HitlProperties;
import dev.toleflaco.erp_purchasing_agent.hitl.MessageMapper;
import dev.toleflaco.erp_purchasing_agent.hitl.PendingToolCall;
import dev.toleflaco.erp_purchasing_agent.hitl.RunStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReActAgentTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private ToolCallingManager toolCallingManager;

    @Mock
    private RunStateRepository runStateRepository;


    private final MessageMapper messageMapper = new MessageMapper();

    private ReActAgent agent;

    static final int DEFAULT_MAX_ITERATIONS = 15;
    static final long DEFAULT_MAX_TOKENS_BUDGET = 10000;
    static final long DEFAULT_MAX_DURATION_MS = 100000;
    static final Clock DEFAULT_CLOCK = Clock.fixed(Instant.parse("2026-09-07T10:00:00Z"), ZoneOffset.UTC);
    static final double DEFAULT_INPUT_COST_PER_MILLION_TOKENS = 3.0;
    static final double DEFAULT_OUTPUT_COST_PER_MILLION_TOKENS = 15.0;
    static final int DEFAULT_PROMPT_TOKENS = 100;
    static final int DEFAULT_COMPLETION_TOKENS = 50;
    static final HitlProperties HITL_OFF = new HitlProperties(Set.of(), Duration.ofHours(24));
    private static final HitlProperties HITL_SENDS_ON = new HitlProperties(Set.of("sendPurchaseOrder"), Duration.ofHours(24));

    @BeforeEach
    void setUp() {
        // instanciar ReActAgent pasando los 9 parametros:
        agent = buildAgent(DEFAULT_MAX_ITERATIONS, DEFAULT_MAX_TOKENS_BUDGET, DEFAULT_MAX_DURATION_MS, DEFAULT_CLOCK);
    }

    @Test
    void shouldReturnFinalTextWhenLlmHasNoToolCalls() {
        // Given
        ChatResponse response = buildResponseWithoutToolCalls("Hi there", DEFAULT_PROMPT_TOKENS, DEFAULT_COMPLETION_TOKENS);
        given(chatModel.call(any(Prompt.class))).willReturn(response);

        // When
        AgentRunResult result = agent.run("hello");
        AgentRunResult.Completed completed = assertInstanceOf(AgentRunResult.Completed.class, result);
        // Then
        assertThat(completed.text()).isEqualTo("Hi there");
        then(chatModel).should(times(1)).call(any(Prompt.class));
    }

    @Test
    void shouldReturnFinalTextWhenLlmHasOneToolCalls() {
        // Given

        ToolResponseMessage.ToolResponse toolResponse = new ToolResponseMessage.ToolResponse("call-1", "get_supplier_by_id", "{\"nombre\":\"ACME\"}");
        List<Message> historyAfterTool = List.of(ToolResponseMessage.builder()
                .responses(List.of(toolResponse))
                .build());
        List<AssistantMessage.ToolCall> toolCalls = List.of(new AssistantMessage.ToolCall("call-1", "function", "get_supplier_by_id", "{\"id\":42}"));
        ChatResponse responseWithToolCalls = buildResponseWithToolCalls(null, toolCalls, DEFAULT_PROMPT_TOKENS, DEFAULT_COMPLETION_TOKENS);
        ChatResponse finalResponse = buildResponseWithoutToolCalls("respuesta final", 200, 30);

        given(toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
                .willReturn(ToolExecutionResult.builder().conversationHistory(historyAfterTool).build());
        given(chatModel.call(any(Prompt.class)))
                .willReturn(responseWithToolCalls, finalResponse);
        // When
        AgentRunResult result = agent.run("cual es el proveedor con id= 42");
        AgentRunResult.Completed completed = assertInstanceOf(AgentRunResult.Completed.class, result);
        // Then
        assertThat(completed.text()).isEqualTo("respuesta final");
        verify(chatModel, times(2)).call(any(Prompt.class));
        verify(toolCallingManager, times(1)).executeToolCalls(any(Prompt.class), any(ChatResponse.class));
    }

    @Test
    void shouldReturnFinalTextAfterMultipleToolIterations() {
        // Given
        ToolResponseMessage.ToolResponse toolResponse = new ToolResponseMessage.ToolResponse(
                "call-1",
                "get_supplier_by_id",
                "{\"nombre\":\"ACME\"}");
        List<Message> historyAfterTool = List.of(ToolResponseMessage.builder()
                .responses(List.of(toolResponse))
                .build());

        List<AssistantMessage.ToolCall> toolCalls = List.of(new AssistantMessage.ToolCall("call-1", "function", "get_supplier_by_id", "{\"id\":42}"));
        ChatResponse responseWithToolCalls1 = buildResponseWithToolCalls(null, toolCalls, DEFAULT_PROMPT_TOKENS, DEFAULT_COMPLETION_TOKENS);
        ChatResponse responseWithToolCalls2 = buildResponseWithToolCalls(null, toolCalls, DEFAULT_PROMPT_TOKENS, DEFAULT_COMPLETION_TOKENS);
        ChatResponse responseWithToolCalls3 = buildResponseWithToolCalls(null, toolCalls, DEFAULT_PROMPT_TOKENS, DEFAULT_COMPLETION_TOKENS);
        ChatResponse finalResponse = buildResponseWithoutToolCalls("respuesta final", 200, 30);

        given(chatModel.call(any(Prompt.class)))
                .willReturn(responseWithToolCalls1, responseWithToolCalls2, responseWithToolCalls3, finalResponse);
        given(toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
                .willReturn(ToolExecutionResult.builder().conversationHistory(historyAfterTool).build());
        // When
        AgentRunResult result = agent.run("cual es el proveedor con id= 42");
        AgentRunResult.Completed completed = assertInstanceOf(AgentRunResult.Completed.class, result);
        // Then
        assertThat(completed.text()).isEqualTo("respuesta final");
        verify(chatModel, times(4)).call(any(Prompt.class));
        verify(toolCallingManager, times(3)).executeToolCalls(any(Prompt.class), any(ChatResponse.class));
    }

    @Test
    void shouldThrowGuardrailExceededWhenMaxIterationsExceeded() {

        // Given
        ToolResponseMessage.ToolResponse toolResponse = new ToolResponseMessage.ToolResponse(
                "call-1",
                "get_supplier_by_id",
                "{\"nombre\":\"ACME\"}");
        List<Message> historyAfterTool = List.of(ToolResponseMessage.builder()
                .responses(List.of(toolResponse))
                .build());
        agent = buildAgent(4);
        List<AssistantMessage.ToolCall> toolCalls = List.of(new AssistantMessage.ToolCall("call-1", "function", "get_supplier_by_id", "{\"id\":42}"));
        ChatResponse responseWithToolCalls = buildResponseWithToolCalls(null, toolCalls, DEFAULT_PROMPT_TOKENS, DEFAULT_COMPLETION_TOKENS);
        given(chatModel.call(any(Prompt.class)))
                .willReturn(responseWithToolCalls, responseWithToolCalls, responseWithToolCalls, responseWithToolCalls, responseWithToolCalls);
        given(toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
                .willReturn(ToolExecutionResult.builder().conversationHistory(historyAfterTool).build());

        // Then
        assertThatThrownBy(() -> agent.run("cual es el proveedor con id= 42"))
                .isInstanceOf(GuardrailExceededException.class)
                .hasFieldOrPropertyWithValue("type", GuardrailExceededException.GuardrailType.ITERATIONS)
                .hasFieldOrPropertyWithValue("value", 4L)
                .hasFieldOrPropertyWithValue("limit", 4L);

    }

    @Test
    void shouldThrowGuardrailExceededWhenMaxDurationExceeded() {
        // Given
        Clock mockClock = mock(Clock.class);
        long maxDurationMs = 150L;
        Instant t0 = Instant.parse("2026-09-07T10:00:00Z");
        Instant t1 = t0.plusMillis(200);  // 200ms después
        agent = buildAgent(DEFAULT_MAX_ITERATIONS, DEFAULT_MAX_TOKENS_BUDGET, maxDurationMs, mockClock);

        ToolResponseMessage.ToolResponse toolResponse = new ToolResponseMessage.ToolResponse(
                "call-1",
                "get_supplier_by_id",
                "{\"nombre\":\"ACME\"}");
        List<Message> historyAfterTool = List.of(ToolResponseMessage.builder()
                .responses(List.of(toolResponse))
                .build());
        List<AssistantMessage.ToolCall> toolCalls = List.of(new AssistantMessage.ToolCall("call-1", "function", "get_supplier_by_id", "{\"id\":42}"));
        ChatResponse responseWithToolCalls = buildResponseWithToolCalls(null, toolCalls, DEFAULT_PROMPT_TOKENS, DEFAULT_COMPLETION_TOKENS);
        given(chatModel.call(any(Prompt.class)))
                .willReturn(responseWithToolCalls);
        given(toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
                .willReturn(ToolExecutionResult.builder().conversationHistory(historyAfterTool).build());
        given(mockClock.instant()).willReturn(t0, t1);
        // Then
        assertThatThrownBy(() -> agent.run("cual es el proveedor con id = 42"))
                .isInstanceOf(GuardrailExceededException.class)
                .hasFieldOrPropertyWithValue("type", GuardrailExceededException.GuardrailType.DURATION)
                .hasFieldOrPropertyWithValue("value", 200L)
                .hasFieldOrPropertyWithValue("limit", maxDurationMs);

    }

    @Test
    void shouldThrowGuardrailExceededWhenMaxTokensBudgetExceeded() {
        // Given
        long maxBudget = 100L;
        agent = buildAgent(DEFAULT_MAX_ITERATIONS, maxBudget, DEFAULT_MAX_DURATION_MS, DEFAULT_CLOCK);

        ToolResponseMessage.ToolResponse toolResponse = new ToolResponseMessage.ToolResponse(
                "call-1",
                "get_supplier_by_id",
                "{\"nombre\":\"ACME\"}");
        List<Message> historyAfterTool = List.of(ToolResponseMessage.builder()
                .responses(List.of(toolResponse))
                .build());
        List<AssistantMessage.ToolCall> toolCalls = List.of(new AssistantMessage.ToolCall("call-1", "function", "get_supplier_by_id", "{\"id\":42}"));
        ChatResponse responseWithToolCalls = buildResponseWithToolCalls(null, toolCalls, DEFAULT_PROMPT_TOKENS, DEFAULT_COMPLETION_TOKENS);
        given(chatModel.call(any(Prompt.class)))
                .willReturn(responseWithToolCalls);
        given(toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
                .willReturn(ToolExecutionResult.builder().conversationHistory(historyAfterTool).build());
        // Then
        assertThatThrownBy(() -> agent.run("cual es el proveedor con id = 42"))
                .isInstanceOf(GuardrailExceededException.class)
                .hasFieldOrPropertyWithValue("type", GuardrailExceededException.GuardrailType.TOKENS)
                .hasFieldOrPropertyWithValue("value", 150L)
                .hasFieldOrPropertyWithValue("limit", maxBudget);

    }

    @Test
    void shouldComputeTotalCostFromTokenUsage() {
        // Given
        ToolResponseMessage.ToolResponse toolResponse = new ToolResponseMessage.ToolResponse(
                "call-1",
                "get_supplier_by_id",
                "{\"nombre\":\"ACME\"}");
        List<Message> historyAfterTool = List.of(ToolResponseMessage.builder()
                .responses(List.of(toolResponse))
                .build());

        List<AssistantMessage.ToolCall> toolCalls = List.of(new AssistantMessage.ToolCall("call-1", "function", "get_supplier_by_id", "{\"id\":42}"));
        ChatResponse responseWithToolCalls = buildResponseWithToolCalls(null, toolCalls, 1000, 500);
        ChatResponse finalResponse = buildResponseWithoutToolCalls("respuesta final", 1000, 500);

        given(chatModel.call(any(Prompt.class)))
                .willReturn(responseWithToolCalls, finalResponse);
        given(toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
                .willReturn(ToolExecutionResult.builder().conversationHistory(historyAfterTool).build());
        // When
        AgentRunResult result = agent.run("cual es el proveedor con id= 42");
        AgentRunResult.Completed completed = assertInstanceOf(AgentRunResult.Completed.class, result);
        // Then
        assertThat(completed.costUsd()).isEqualTo(0.021, within(1e-9));
    }

    @Test
    void shouldReturnPausedWhenLlmCallsSensitiveTool() {

        // Given
        List<AssistantMessage.ToolCall> toolCalls = List.of(
                new AssistantMessage.ToolCall(
                        "call-1",
                        "function",
                        "sendPurchaseOrder",
                        "{\"purchaseOrderId\":42}"

                )
        );
        ChatResponse responseWithSensitiveTool = buildResponseWithToolCalls(
                null, toolCalls, DEFAULT_PROMPT_TOKENS, DEFAULT_COMPLETION_TOKENS);
        given(chatModel.call(any(Prompt.class))).willReturn(responseWithSensitiveTool);

        agent = buildAgent(DEFAULT_MAX_ITERATIONS, DEFAULT_MAX_TOKENS_BUDGET, DEFAULT_MAX_DURATION_MS, DEFAULT_CLOCK, HITL_SENDS_ON);

        // When
        AgentRunResult result = agent.run("envia la orden 42");
        AgentRunResult.Paused paused = assertInstanceOf(AgentRunResult.Paused.class, result);

        // Then
        assertThat(paused.pendingToolCalls())
                .hasSize(1)
                .first()
                .extracting(PendingToolCall::name, PendingToolCall::arguments)
                .containsExactly("sendPurchaseOrder", "{\"purchaseOrderId\":42}");

        assertThat(paused.iterations()).isEqualTo(1);
        then(toolCallingManager).should(never()).executeToolCalls(any(), any());
        then(runStateRepository).should(times(1)).save(any(), any());
    }

    @Test
    void shouldReturnAllPendingToolCallsWhenLlmMixesSensitiveAndSafeTools() {
        // Given
        List<AssistantMessage.ToolCall> toolCalls = List.of(
                new AssistantMessage.ToolCall(
                        "call-1",
                        "function",
                        "sendPurchaseOrder",
                        "{\"purchaseOrderId\":42}"
                ),
                new AssistantMessage.ToolCall(
                        "call-2",
                        "function",
                        "getSupplier",
                        "{\"supplierId\":7}"
                )
        );
        ChatResponse responseWithSensitiveTool = buildResponseWithToolCalls(
                null, toolCalls, DEFAULT_PROMPT_TOKENS, DEFAULT_COMPLETION_TOKENS);
        given(chatModel.call(any(Prompt.class))).willReturn(responseWithSensitiveTool);

        agent = buildAgent(DEFAULT_MAX_ITERATIONS, DEFAULT_MAX_TOKENS_BUDGET, DEFAULT_MAX_DURATION_MS, DEFAULT_CLOCK, HITL_SENDS_ON);

        // When
        AgentRunResult result = agent.run("envia la orden 42");
        AgentRunResult.Paused paused = assertInstanceOf(AgentRunResult.Paused.class, result);

        // Then
        assertThat(paused.pendingToolCalls())
                .hasSize(2)
                .extracting(PendingToolCall::name, PendingToolCall::arguments)
                .containsExactly(tuple("sendPurchaseOrder", "{\"purchaseOrderId\":42}"), tuple("getSupplier", "{\"supplierId\":7}"));

        assertThat(paused.iterations()).isEqualTo(1);
        then(toolCallingManager).should(never()).executeToolCalls(any(), any());
        then(runStateRepository).should(times(1)).save(any(), any());
    }

    @Test
    void shouldReturnCompletedWhenResumeExecutesPendingToolAndLlmFinishes() {

        // Given
        List<AssistantMessage.ToolCall> toolCalls = List.of(
                new AssistantMessage.ToolCall(
                        "call-1",
                        "function",
                        "sendPurchaseOrder",
                        "{\"purchaseOrderId\":42}"

                )
        );
        UserMessage userMessage = new UserMessage("Mensaje usuario");
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content(null)
                .toolCalls(toolCalls)
                .build();
        List<AgentMessage> history = messageMapper.toAgent(List.of(userMessage, assistantMessage));

        AgentRunSession session = new AgentRunSession(
                "run-123",
                5000L,
                500L,
                0.002,
                1,
                history
        );
        given(runStateRepository.findById("run-123"))
                .willReturn(Optional.of(session));
        ToolResponseMessage.ToolResponse toolResponse =
                new ToolResponseMessage.ToolResponse("call-1", "sendPurchaseOrder", "resultado");
        given(toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
                .willReturn(ToolExecutionResult.builder().conversationHistory(
                                List.of(ToolResponseMessage.builder().responses(List.of(toolResponse)).build()))
                        .build());
        ChatResponse responseWithSensitiveTool = buildResponseWithoutToolCalls("orden enviada", DEFAULT_PROMPT_TOKENS, DEFAULT_COMPLETION_TOKENS);
        given(chatModel.call(any(Prompt.class))).willReturn(responseWithSensitiveTool);
        agent = buildAgent(DEFAULT_MAX_ITERATIONS, DEFAULT_MAX_TOKENS_BUDGET, DEFAULT_MAX_DURATION_MS, DEFAULT_CLOCK, HITL_SENDS_ON);

        // When
        AgentRunResult result = agent.resume("run-123");

        // Then
        AgentRunResult.Completed completed = assertInstanceOf(AgentRunResult.Completed.class, result);
        assertThat(completed.text()).isEqualTo("orden enviada");
        assertThat(completed.iterations()).isEqualTo(2);
        assertThat(completed.tokensTotal()).isEqualTo(650);
        then(runStateRepository).should().deleteById("run-123");

    }

    @Test
    void shouldReturnPausedAgainWhenResumeHitsAnotherSensitiveTool() {


        // Given
        List<AssistantMessage.ToolCall> toolCalls = List.of(
                new AssistantMessage.ToolCall(
                        "call-1",
                        "function",
                        "sendPurchaseOrder",
                        "{\"purchaseOrderId\":42}"

                )
        );
        UserMessage userMessage = new UserMessage("Mensaje usuario");
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content(null)
                .toolCalls(toolCalls)
                .build();
        List<AgentMessage> history = messageMapper.toAgent(List.of(userMessage, assistantMessage));

        AgentRunSession session = new AgentRunSession(
                "run-123",
                5000L,
                500L,
                0.002,
                1,
                history
        );
        given(runStateRepository.findById("run-123"))
                .willReturn(Optional.of(session));
        ToolResponseMessage.ToolResponse toolResponse =
                new ToolResponseMessage.ToolResponse("call-1", "sendPurchaseOrder", "resultado");
        given(toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
                .willReturn(ToolExecutionResult.builder().conversationHistory(
                                List.of(ToolResponseMessage.builder().responses(List.of(toolResponse)).build()))
                        .build());
        ChatResponse responseWithoutToolCalls = buildResponseWithToolCalls(null, toolCalls, DEFAULT_PROMPT_TOKENS, DEFAULT_COMPLETION_TOKENS);
        given(chatModel.call(any(Prompt.class))).willReturn(responseWithoutToolCalls);
        agent = buildAgent(DEFAULT_MAX_ITERATIONS, DEFAULT_MAX_TOKENS_BUDGET, DEFAULT_MAX_DURATION_MS, DEFAULT_CLOCK, HITL_SENDS_ON);

        // When
        AgentRunResult result = agent.resume("run-123");

        // Then
        AgentRunResult.Paused paused = assertInstanceOf(AgentRunResult.Paused.class, result);
        assertThat(paused.pendingToolCalls()).hasSize(1);
        assertThat(paused.originalPrompt()).isEqualTo("Mensaje usuario");
        assertThat(paused.pendingToolCalls().getFirst().name()).isEqualTo("sendPurchaseOrder");
        assertThat(paused.pendingToolCalls().getFirst().arguments()).isEqualTo("{\"purchaseOrderId\":42}");
        assertThat(paused.iterations()).isEqualTo(2);
        assertThat(paused.tokensTotal()).isEqualTo(650);
        then(runStateRepository).should(times(1)).save(any(), any());
        then(runStateRepository).should(never()).deleteById(any());

    }

    // Helper para construir un ChatResponse "sin tool calls" con texto y tokens.
    private ChatResponse buildResponseWithoutToolCalls(String text, int promptTokens, int completionTokens) {
        // 1. Generation (mensaje del asistente + metadata de generacion)
        Generation generation = new Generation(
                new AssistantMessage(text),
                ChatGenerationMetadata.builder().finishReason("end_turn").build()
        );

        // 2. Usage (mock, porque Usage es interface)
        Usage usage = mock(Usage.class);
        given(usage.getPromptTokens()).willReturn(promptTokens);
        given(usage.getCompletionTokens()).willReturn(completionTokens);

        // 3. Response metadata (agrega el Usage)
        ChatResponseMetadata responseMetadata = ChatResponseMetadata.builder()
                .usage(usage)
                .build();

        // 4. Response final
        return new ChatResponse(List.of(generation), responseMetadata);
    }

    private ChatResponse buildResponseWithToolCalls(
            String content,
            List<AssistantMessage.ToolCall> toolCalls,
            int promptTokens,
            int completionTokens
    ) {
        Generation generation = new Generation(
                AssistantMessage.builder()
                        .content(content)
                        .toolCalls(toolCalls)
                        .build(),
                ChatGenerationMetadata.builder().finishReason("tool_use").build()
        );
        // 2. Usage (mock, porque Usage es interface)
        Usage usage = mock(Usage.class);
        given(usage.getPromptTokens()).willReturn(promptTokens);
        given(usage.getCompletionTokens()).willReturn(completionTokens);

        // 3. Response metadata (agrega el Usage)
        ChatResponseMetadata responseMetadata = ChatResponseMetadata.builder()
                .usage(usage)
                .build();

        // 4. Response final
        return new ChatResponse(List.of(generation), responseMetadata);
    }

    private ReActAgent buildAgent(int maxIterations, long maxTokensBudget, long maxDurationMs, Clock clock, HitlProperties hitl) {
        LlmPricingProperties llmPricing = new LlmPricingProperties(
                DEFAULT_INPUT_COST_PER_MILLION_TOKENS,
                DEFAULT_OUTPUT_COST_PER_MILLION_TOKENS);

        AgentGuardrailsProperties agentGuardrails = new AgentGuardrailsProperties(
                maxIterations,
                maxTokensBudget,
                maxDurationMs);

        return new ReActAgent(
                chatModel,
                List.of(),
                toolCallingManager,
                clock,
                llmPricing,
                agentGuardrails,
                hitl,
                runStateRepository,
                messageMapper
        );
    }

    private ReActAgent buildAgent(int maxIterations, long maxTokensBudget, long maxDurationMs, Clock clock) {
        return buildAgent(maxIterations, maxTokensBudget, maxDurationMs, clock, HITL_OFF);
    }

    private ReActAgent buildAgent(int maxIterations) {
        return buildAgent(maxIterations, DEFAULT_MAX_TOKENS_BUDGET, DEFAULT_MAX_DURATION_MS, DEFAULT_CLOCK);
    }

    private ReActAgent buildAgent(Clock mockClock) {
        return buildAgent(DEFAULT_MAX_ITERATIONS, DEFAULT_MAX_TOKENS_BUDGET, DEFAULT_MAX_DURATION_MS, mockClock);
    }
}
