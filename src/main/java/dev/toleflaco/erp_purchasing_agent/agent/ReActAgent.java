package dev.toleflaco.erp_purchasing_agent.agent;


import dev.toleflaco.erp_purchasing_agent.config.AgentGuardrailsProperties;
import dev.toleflaco.erp_purchasing_agent.config.LlmPricingProperties;
import dev.toleflaco.erp_purchasing_agent.exception.GuardrailExceededException;
import io.modelcontextprotocol.client.McpSyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static dev.toleflaco.erp_purchasing_agent.exception.GuardrailExceededException.GuardrailType.*;


@Service
public class ReActAgent {
    private static final Logger log = LoggerFactory.getLogger(ReActAgent.class);
    private final ChatModel chatModel;
    private final List<McpSyncClient> mcpClients;
    private final ToolCallingManager toolCallingManager;
    private final Clock clock;
    private final LlmPricingProperties llmPricing;
    private final AgentGuardrailsProperties agentGuardrails;

    public ReActAgent(ChatModel chatModel,
                      List<McpSyncClient> mcpClients,
                      ToolCallingManager toolCallingManager,
                      Clock clock,
                      LlmPricingProperties llmPricing,
                      AgentGuardrailsProperties agentGuardrails) {
        this.chatModel = chatModel;
        this.mcpClients = mcpClients;
        this.toolCallingManager = toolCallingManager;
        this.clock = clock;
        this.llmPricing = llmPricing;
        this.agentGuardrails = agentGuardrails;

    }

    public AgentRunResult run(String prompt) {
        // 1. Preparación (una sola vez)
        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(prompt));
        ToolCallback[] toolCallbacks = new SyncMcpToolCallbackProvider(mcpClients).getToolCallbacks();
        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .toolCallbacks(toolCallbacks)
                .build();

        int iteration = 0;
        int totalPromptTokens = 0;
        int totalCompletionTokens = 0;
        Instant start = clock.instant();


        Prompt currentPrompt = new Prompt(messages, options);
        log.debug("iteration start iteration={} messages_size={} tokens_accumulated={}", iteration + 1, 1, 0);
        // 2. Primera llamada al LLM (fuera del while para inicializar la condición)
        ChatResponse response = chatModel.call(currentPrompt);
        iteration++;
        logLlmResponse(response, iteration);
        totalPromptTokens += response.getMetadata().getUsage().getPromptTokens();
        totalCompletionTokens += response.getMetadata().getUsage().getCompletionTokens();
        // 3. Bucle ReAct: mientras el LLM siga pidiendo tools, itera
        while (response.hasToolCalls()) {
            ToolExecutionResult result = toolCallingManager.executeToolCalls(currentPrompt, response);
            log.debug("iteration start iteration={} messages_size={} tokens_accumulated={}", iteration + 1, result.conversationHistory().size(), totalPromptTokens + totalCompletionTokens);
            ToolResponseMessage toolResponseMessage = (ToolResponseMessage) result.conversationHistory().get(result.conversationHistory().size() - 1);
            for (ToolResponseMessage.ToolResponse tr : toolResponseMessage.getResponses()) {
                String resultToLog = tr.responseData().length() > 200 ? tr.responseData().substring(0, 200) + "...(truncated, " + tr.responseData().length() + " total chars)" : tr.responseData();
                log.debug("tool result iteration={} tool_name={} tool_call_id={} result={}", iteration, tr.name(), tr.id(), resultToLog);
            }
            currentPrompt = new Prompt(result.conversationHistory(), options);
            if (iteration >= agentGuardrails.maxIterations()) {
                log.debug("guardrail exceeded type={} value={} limit={}", ITERATIONS, iteration, agentGuardrails.maxIterations());
                throw new GuardrailExceededException(ITERATIONS, iteration, agentGuardrails.maxIterations());
            }
            long totalTokens = totalPromptTokens + totalCompletionTokens;
            if (totalTokens >= agentGuardrails.maxTokensBudget()) {
                log.debug("guardrail exceeded type={} value={} limit={}", TOKENS, totalTokens, agentGuardrails.maxTokensBudget());
                throw new GuardrailExceededException(TOKENS, totalTokens, agentGuardrails.maxTokensBudget());
            }
            long elapsedMs = Duration.between(start, clock.instant()).toMillis();
            if (elapsedMs >= agentGuardrails.maxDurationMs()) {
                log.debug("guardrail exceeded type={} value={} limit={}", DURATION, elapsedMs, agentGuardrails.maxDurationMs());
                throw new GuardrailExceededException(DURATION, elapsedMs, agentGuardrails.maxDurationMs());
            }
            response = chatModel.call(currentPrompt);
            iteration++;
            logLlmResponse(response, iteration);
            totalPromptTokens += response.getMetadata().getUsage().getPromptTokens();
            totalCompletionTokens += response.getMetadata().getUsage().getCompletionTokens();
        }

        // 4. Respuesta final del LLM sin tool calls
        double cost = (totalPromptTokens / 1_000_000.0) * llmPricing.inputPerMtok() + (totalCompletionTokens / 1_000_000.0) * llmPricing.outputPerMtok();
        long durationMs = Duration.between(start, clock.instant()).toMillis();
        String finalText = response.getResult().getOutput().getText();
        log.debug("agent run completed iterations={} tokens_total={} duration_ms={} cost_usd={}", iteration,
                totalPromptTokens + totalCompletionTokens,
                durationMs,
                String.format("%.6f", cost));
        return new AgentRunResult(
                finalText,
                iteration,
                totalPromptTokens + totalCompletionTokens,
                durationMs,
                cost
        );
    }

    private String formatToolCalls(List<AssistantMessage.ToolCall> toolCalls) {

        return toolCalls.stream()
                .map(tc -> tc.name() + "(" + tc.arguments() + ")")
                .collect(Collectors.joining(", ", "[", "]"));

    }

    private void logLlmResponse(ChatResponse response, int iteration) {
        String text = response.getResult().getOutput().getText();
        String toolCalls = formatToolCalls(response.getResult().getOutput().getToolCalls());
        String finishReason = response.getResult().getMetadata().getFinishReason();
        Integer promptTokens = response.getMetadata().getUsage().getPromptTokens();
        Integer completionTokens = response.getMetadata().getUsage().getCompletionTokens();
        if (text == null || text.isBlank()) {
            log.debug("llm response iteration={} finish_reason={} tool_calls={} tokens_in={} tokens_out={}", iteration, finishReason, toolCalls, promptTokens, completionTokens);
        } else {
            String textToLog = text.length() > 200 ? text.substring(0, 200) + "...(truncated, " + text.length() + " total chars)" : text;
            log.debug("llm response iteration={} finish_reason={} tool_calls={} tokens_in={} tokens_out={} text={}", iteration, finishReason, toolCalls, promptTokens, completionTokens, textToLog);
        }
    }
}
