package dev.toleflaco.erp_purchasing_agent.agent;


import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ReActAgent {
    private final ChatModel chatModel;
    private final List<McpSyncClient> mcpClients;

    public ReActAgent(ChatModel chatModel, List<McpSyncClient> mcpClients) {
        this.chatModel = chatModel;
        this.mcpClients = mcpClients;
    }

    public String run(String prompt) {
        // 1. Preparación (una sola vez)
        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(prompt));
        ToolCallback[] toolCallbacks = new SyncMcpToolCallbackProvider(mcpClients).getToolCallbacks();
        ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .toolCallbacks(toolCallbacks)
                .build();
        Prompt currentPrompt = new Prompt(messages, options);

        // 2. Primera llamada al LLM (fuera del while para inicializar la condición)
        ChatResponse response = chatModel.call(currentPrompt);

        // 3. Bucle ReAct: mientras el LLM siga pidiendo tools, itera
        while (response.hasToolCalls()) {
            ToolExecutionResult result = toolCallingManager.executeToolCalls(currentPrompt, response);
            currentPrompt = new Prompt(result.conversationHistory(), options);
            response = chatModel.call(currentPrompt);
        }

        // 4. Respuesta final del LLM sin tool calls
        return response.getResult().getOutput().getText();
    }
}
