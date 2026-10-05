package dev.toleflaco.erp_purchasing_agent.agent;


import dev.toleflaco.erp_purchasing_agent.config.AgentGuardrailsProperties;
import dev.toleflaco.erp_purchasing_agent.config.LlmPricingProperties;
import dev.toleflaco.erp_purchasing_agent.exception.GuardrailExceededException;
import dev.toleflaco.erp_purchasing_agent.exception.RunSessionNotFoundException;
import dev.toleflaco.erp_purchasing_agent.hitl.AgentMessage;
import dev.toleflaco.erp_purchasing_agent.hitl.AgentRunSession;
import dev.toleflaco.erp_purchasing_agent.hitl.HitlProperties;
import dev.toleflaco.erp_purchasing_agent.hitl.MessageMapper;
import dev.toleflaco.erp_purchasing_agent.hitl.RunStateRepository;
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
import org.springframework.ai.chat.model.Generation;
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
import java.util.Optional;
import java.util.UUID;
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
    private final HitlProperties hitlProperties;
    private final RunStateRepository repository;
    private final MessageMapper mapper;

    public ReActAgent(ChatModel chatModel,
                      List<McpSyncClient> mcpClients,
                      ToolCallingManager toolCallingManager,
                      Clock clock,
                      LlmPricingProperties llmPricing,
                      AgentGuardrailsProperties agentGuardrails,
                      HitlProperties hitlProperties,
                      RunStateRepository repository,
                      MessageMapper mapper) {
        this.chatModel = chatModel;
        this.mcpClients = mcpClients;
        this.toolCallingManager = toolCallingManager;
        this.clock = clock;
        this.llmPricing = llmPricing;
        this.agentGuardrails = agentGuardrails;
        this.hitlProperties = hitlProperties;
        this.repository = repository;
        this.mapper = mapper;
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
        long totalTokens = 0L;
        double accumulatedCost = 0.0;
        int promptTokens;
        int completionTokens;
        Instant start = clock.instant();


        Prompt currentPrompt = new Prompt(messages, options);
        log.debug("iteration start iteration={} messages_size={} tokens_accumulated={}", iteration + 1, 1, 0);
        // 2. Primera llamada al LLM (fuera del while para inicializar la condición)
        ChatResponse response = chatModel.call(currentPrompt);
        iteration++;
        logLlmResponse(response, iteration);
        promptTokens = response.getMetadata().getUsage().getPromptTokens();
        completionTokens = response.getMetadata().getUsage().getCompletionTokens();
        totalTokens += promptTokens + completionTokens;
        accumulatedCost += computeCost(promptTokens, completionTokens);
        // 3. Bucle ReAct: mientras el LLM siga pidiendo tools, itera
        while (response.hasToolCalls()) {
            // Extraer las tools calls
            List<AssistantMessage.ToolCall> toolCalls = response.getResult().getOutput().getToolCalls();
            Optional<AssistantMessage.ToolCall> sensitiveToolCall = toolCalls.stream()
                    .filter(toolCall -> hitlProperties.sensitiveTools().contains(toolCall.name())).findFirst();
            if (sensitiveToolCall.isPresent()) {
                String runId = UUID.randomUUID().toString();
                long durationMs = computeElapsedMs(start);
                List<Message> fullHistory = new ArrayList<>(currentPrompt.getInstructions());
                fullHistory.add(response.getResult().getOutput());
                List<AgentMessage> history = mapper.toAgent(fullHistory);
                AgentRunSession session = new AgentRunSession(
                        runId,
                        durationMs,
                        totalTokens,
                        accumulatedCost,
                        iteration,
                        history
                );
                repository.save(session, hitlProperties.ttl());
                return new AgentRunResult.Paused(
                        runId,
                        prompt,
                        mapper.toPendingToolCalls(toolCalls),
                        iteration,
                        totalTokens,
                        durationMs,
                        accumulatedCost
                );
            }
            ToolExecutionResult result = toolCallingManager.executeToolCalls(currentPrompt, response);
            log.debug("iteration start iteration={} messages_size={} tokens_accumulated={}", iteration + 1, result.conversationHistory().size(), totalTokens);
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
            if (totalTokens >= agentGuardrails.maxTokensBudget()) {
                log.debug("guardrail exceeded type={} value={} limit={}", TOKENS, totalTokens, agentGuardrails.maxTokensBudget());
                throw new GuardrailExceededException(TOKENS, totalTokens, agentGuardrails.maxTokensBudget());
            }
            long elapsedMs = computeElapsedMs(start);
            if (elapsedMs >= agentGuardrails.maxDurationMs()) {
                log.debug("guardrail exceeded type={} value={} limit={}", DURATION, elapsedMs, agentGuardrails.maxDurationMs());
                throw new GuardrailExceededException(DURATION, elapsedMs, agentGuardrails.maxDurationMs());
            }
            response = chatModel.call(currentPrompt);
            iteration++;
            logLlmResponse(response, iteration);
            promptTokens = response.getMetadata().getUsage().getPromptTokens();
            completionTokens = response.getMetadata().getUsage().getCompletionTokens();
            totalTokens += promptTokens + completionTokens;
            accumulatedCost += computeCost(promptTokens, completionTokens);
        }

        // 4. Respuesta final del LLM sin tool calls
        long durationMs = computeElapsedMs(start);
        String finalText = response.getResult().getOutput().getText();
        log.debug("agent run completed iterations={} tokens_total={} duration_ms={} cost_usd={}", iteration,
                totalTokens,
                durationMs,
                String.format("%.6f", accumulatedCost));
        return new AgentRunResult.Completed(
                finalText,
                iteration,
                totalTokens,
                durationMs,
                accumulatedCost
        );
    }

    public AgentRunResult resume(String runId) {

        AgentRunSession session = repository.findById(runId)
                .orElseThrow(() -> new RunSessionNotFoundException(runId));
        long iteration = session.iterations();
        long totalTokens = session.totalTokens();
        double accumulatedCost = session.costUsd();
        // Es sintético el start, se le resta lo que lleva de las otras llamadas, para que el bucle quede lo más parecido al de run()
        Instant start = clock.instant().minus(Duration.ofMillis(session.usefulDurationMs()));
        log.debug("resume start runId={} iteration={} tokens_accumulated={} duration_ms_accumulated={}",
                runId, iteration, totalTokens, session.usefulDurationMs());

        List<Message> messages = mapper.toSpringAi(session.conversationHistory());
        String originalPrompt = ((UserMessage) messages.getFirst()).getText();
        ToolCallback[] toolCallbacks = new SyncMcpToolCallbackProvider(mcpClients).getToolCallbacks();
        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .toolCallbacks(toolCallbacks)
                .build();
        AssistantMessage lastAssistant = (AssistantMessage) messages.getLast();
        ChatResponse response = new ChatResponse(List.of(new Generation(lastAssistant)));
        Prompt currentPrompt = new Prompt(messages.subList(0, messages.size() - 1), options);

        ToolExecutionResult result = toolCallingManager.executeToolCalls(currentPrompt, response);
        currentPrompt = new Prompt(result.conversationHistory(), options);
        response = chatModel.call(currentPrompt);
        iteration++;
        int promptTokens = response.getMetadata().getUsage().getPromptTokens();
        int completionTokens = response.getMetadata().getUsage().getCompletionTokens();
        totalTokens += promptTokens + completionTokens;
        accumulatedCost += computeCost(promptTokens, completionTokens);

        while (response.hasToolCalls()) {
            // Extraer las tools calls
            List<AssistantMessage.ToolCall> toolCalls = response.getResult().getOutput().getToolCalls();
            Optional<AssistantMessage.ToolCall> sensitiveToolCall = toolCalls.stream()
                    .filter(toolCall -> hitlProperties.sensitiveTools().contains(toolCall.name())).findFirst();
            if (sensitiveToolCall.isPresent()) {
                long durationMs = computeElapsedMs(start);
                List<Message> fullHistory = new ArrayList<>(currentPrompt.getInstructions());
                fullHistory.add(response.getResult().getOutput());
                List<AgentMessage> history = mapper.toAgent(fullHistory);
                session = new AgentRunSession(
                        runId,
                        durationMs,
                        totalTokens,
                        accumulatedCost,
                        iteration,
                        history
                );
                repository.save(session, hitlProperties.ttl());
                return new AgentRunResult.Paused(
                        runId,
                        originalPrompt,
                        mapper.toPendingToolCalls(toolCalls),
                        iteration,
                        totalTokens,
                        durationMs,
                        accumulatedCost
                );
            }
            result = toolCallingManager.executeToolCalls(currentPrompt, response);
            log.debug("iteration start iteration={} messages_size={} tokens_accumulated={}", iteration + 1, result.conversationHistory().size(), totalTokens);
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
            if (totalTokens >= agentGuardrails.maxTokensBudget()) {
                log.debug("guardrail exceeded type={} value={} limit={}", TOKENS, totalTokens, agentGuardrails.maxTokensBudget());
                throw new GuardrailExceededException(TOKENS, totalTokens, agentGuardrails.maxTokensBudget());
            }
            long elapsedMs = computeElapsedMs(start);
            if (elapsedMs >= agentGuardrails.maxDurationMs()) {
                log.debug("guardrail exceeded type={} value={} limit={}", DURATION, elapsedMs, agentGuardrails.maxDurationMs());
                throw new GuardrailExceededException(DURATION, elapsedMs, agentGuardrails.maxDurationMs());
            }
            response = chatModel.call(currentPrompt);
            iteration++;
            logLlmResponse(response, (int) iteration);
            promptTokens = response.getMetadata().getUsage().getPromptTokens();
            completionTokens = response.getMetadata().getUsage().getCompletionTokens();
            totalTokens += promptTokens + completionTokens;
            accumulatedCost += computeCost(promptTokens, completionTokens);

        }
        repository.deleteById(runId);
        String finalText = response.getResult().getOutput().getText();
        long durationMs = computeElapsedMs(start);
        log.debug("agent run completed iterations={} tokens_total={} duration_ms={} cost_usd={}",
                iteration, totalTokens, durationMs, String.format("%.6f", accumulatedCost));
        return new AgentRunResult.Completed(
                finalText,
                iteration,
                totalTokens,
                durationMs,
                accumulatedCost
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

    private double computeCost(int promptToken, int completionToken) {
        return (promptToken / 1_000_000.0) * llmPricing.inputPerMtok() + (completionToken / 1_000_000.0) * llmPricing.outputPerMtok();
    }

    private long computeElapsedMs(Instant start) {
        return Duration.between(start, clock.instant()).toMillis();
    }
}
