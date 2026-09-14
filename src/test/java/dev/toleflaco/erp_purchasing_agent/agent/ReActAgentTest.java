package dev.toleflaco.erp_purchasing_agent.agent;

import dev.toleflaco.erp_purchasing_agent.config.AgentGuardrailsProperties;
import dev.toleflaco.erp_purchasing_agent.config.LlmPricingProperties;
import dev.toleflaco.erp_purchasing_agent.exception.GuardrailExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
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

    private ReActAgent agent;

    static final int DEFAULT_MAX_ITERATIONS = 15;
    static final long DEFAULT_MAX_TOKENS_BUDGET = 10000;
    static final long DEFAULT_MAX_DURATION_MS = 100000;
    static final Clock DEFAULT_CLOCK = Clock.fixed(Instant.parse("2026-09-07T10:00:00Z"), ZoneOffset.UTC);
    static final double DEFAULT_INPUT_COST_PER_MILLION_TOKENS = 3.0;
    static final double DEFAULT_OUTPUT_COST_PER_MILLION_TOKENS = 15.0;
    static final int DEFAULT_PROMPT_TOKENS = 100;
    static final int DEFAULT_COMPLETION_TOKENS = 50;

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

        // Then
        assertThat(result.text()).isEqualTo("Hi there");
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

        // Then
        assertThat(result.text()).isEqualTo("respuesta final");
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

        // Then
        assertThat(result.text()).isEqualTo("respuesta final");
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

        // Then
        assertThat(result.costUsd()).isEqualTo(0.021, within(1e-9));
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

    private ReActAgent buildAgent(int maxIterations, long maxTokensBudget, long maxDurationMs, Clock clock) {
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
                agentGuardrails
        );
    }

    private ReActAgent buildAgent(int maxIterations) {
        return buildAgent(maxIterations, DEFAULT_MAX_TOKENS_BUDGET, DEFAULT_MAX_DURATION_MS, DEFAULT_CLOCK);
    }

    private ReActAgent buildAgent(Clock mockClock) {
        return buildAgent(DEFAULT_MAX_ITERATIONS, DEFAULT_MAX_TOKENS_BUDGET, DEFAULT_MAX_DURATION_MS, mockClock);
    }
}
