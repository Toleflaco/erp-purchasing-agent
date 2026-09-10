package dev.toleflaco.erp_purchasing_agent.agent;

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

import static org.assertj.core.api.Assertions.assertThat;
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
    private Clock fixedClock;


    @BeforeEach
    void setUp() {
        // crear el fixedClock (Clock.fixed(...) con una fecha determinista).
        fixedClock = Clock.fixed(Instant.parse("2026-09-07T10:00:00Z"), ZoneOffset.UTC);
        // instanciar ReActAgent pasando los 9 parametros:
        agent = new ReActAgent(chatModel,
                List.of(),
                3.0,
                15.0,
                15,
                10000,
                100000,
                toolCallingManager,
                fixedClock);

    }

    @Test
    void shouldReturnFinalTextWhenLlmHasNoToolCalls() {
        // Given
        ChatResponse response = buildResponseWithoutToolCalls("Hi there", 100, 50);
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
        List<AssistantMessage.ToolCall> toolCalls = List.of(new AssistantMessage.ToolCall("call-1","function","get_supplier_by_id", "{\"id\":42}"));
        ChatResponse responseWithToolCalls = buildResponseWithToolCalls(null,toolCalls,100,50);
        ChatResponse finalResponse = buildResponseWithoutToolCalls("respuesta final",200,30);

        given(toolCallingManager.executeToolCalls(any(Prompt.class),any(ChatResponse.class)))
                .willReturn(ToolExecutionResult.builder().conversationHistory(historyAfterTool).build());
        given(chatModel.call(any(Prompt.class)))
                .willReturn(responseWithToolCalls, finalResponse);
        // When
        AgentRunResult result = agent.run("cual es el proveedor con id= 42");

        // Then
        assertThat(result.text()).isEqualTo("respuesta final");
        verify(chatModel, times(2)).call(any(Prompt.class));
        verify(toolCallingManager, times(1)).executeToolCalls(any(Prompt.class),any(ChatResponse.class));
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

}
