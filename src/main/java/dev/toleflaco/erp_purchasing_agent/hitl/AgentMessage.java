package dev.toleflaco.erp_purchasing_agent.hitl;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record AgentMessage(
        Role role,
        @Nullable String text,
        List<AgentToolCall> toolCalls,
        List<AgentToolResponse> toolResponses
) {
    public enum Role { SYSTEM, USER, ASSISTANT, TOOL }

    public record AgentToolCall(String id, String name, String arguments) {}

    public record AgentToolResponse(String id, String name, String responseData) {}
}
