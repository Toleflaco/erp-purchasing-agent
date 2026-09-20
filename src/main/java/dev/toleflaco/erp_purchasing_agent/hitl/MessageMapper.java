package dev.toleflaco.erp_purchasing_agent.hitl;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.List;

import static dev.toleflaco.erp_purchasing_agent.hitl.AgentMessage.Role.*;

@Component
public class MessageMapper {


    public List<AgentMessage> toAgent(List<Message> messages) {
        return messages.stream()
                .map(this::toAgentSingle)
                .toList();
    }

    public List<Message> toSpringAi(List<AgentMessage> messages) {
        return messages.stream()
                .map(this::toSpringAiSingle)
                .toList();
    }


    private AgentMessage toAgentSingle(Message message) {
        return switch (message) {
            case SystemMessage sm -> toAgentSystem(sm);
            case UserMessage um -> toAgentUser(um);
            case AssistantMessage am -> toAgentAssistant(am);
            case ToolResponseMessage tm -> toAgentTool(tm);
            default -> throw new IllegalArgumentException("Unknown message type: " + message.getClass());
        };
    }

    private Message toSpringAiSingle(AgentMessage message) {
        return switch (message.role()) {
            case SYSTEM -> toSpringAiSystem(message);
            case USER -> toSpringAiUser(message);
            case ASSISTANT -> toSpringAiAssistant(message);
            case TOOL -> toSpringAiTool(message);
        };
    }

    private ToolResponseMessage toSpringAiTool(AgentMessage message) {
        return ToolResponseMessage.builder()
                .responses(message.toolResponses().stream()
                        .map(response -> new ToolResponseMessage.ToolResponse(response.id(), response.name(), response.responseData()))
                        .toList())
                .build();
    }

    private AssistantMessage toSpringAiAssistant(AgentMessage message) {
        if (message.toolCalls().isEmpty()) {
            return new AssistantMessage(message.text());
        }
        return AssistantMessage.builder()
                .content(message.text()).toolCalls(message.toolCalls().stream()
                        .map(toolCall -> new AssistantMessage.ToolCall(toolCall.id(), "function", toolCall.name(), toolCall.arguments()))
                        .toList())
                .build();
    }

    private UserMessage toSpringAiUser(AgentMessage message) {
        return new UserMessage(message.text());
    }

    private SystemMessage toSpringAiSystem(AgentMessage message) {
        return new SystemMessage(message.text());
    }

    private AgentMessage toAgentSystem(SystemMessage systemMessage) {
        return new AgentMessage(SYSTEM, systemMessage.getText(), List.of(), List.of());

    }

    private AgentMessage toAgentUser(UserMessage userMessagem) {
        return new AgentMessage(USER, userMessagem.getText(), List.of(), List.of());
    }


    private AgentMessage toAgentAssistant(AssistantMessage assistantMessage) {
        return new AgentMessage(ASSISTANT,
                assistantMessage.getText(),
                assistantMessage.getToolCalls().stream()
                        .map(toolCall -> new AgentMessage.AgentToolCall(toolCall.id(), toolCall.name(), toolCall.arguments()))
                        .toList(),
                List.of());
    }

    private AgentMessage toAgentTool(ToolResponseMessage toolResponseMessage) {

        return new AgentMessage(TOOL,
                null,
                List.of(),
                toolResponseMessage.getResponses().stream()
                        .map(toolResponse -> new AgentMessage.AgentToolResponse(toolResponse.id(), toolResponse.name(), toolResponse.responseData()))
                        .toList());
    }
}
