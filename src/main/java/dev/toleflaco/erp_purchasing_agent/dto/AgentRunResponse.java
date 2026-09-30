package dev.toleflaco.erp_purchasing_agent.dto;

import dev.toleflaco.erp_purchasing_agent.hitl.PendingToolCall;

import java.util.List;

public sealed interface AgentRunResponse
        permits AgentRunResponse.Completed, AgentRunResponse.Paused {

    record Completed(
            String text,
            long iterations,
            long tokensTotal,
            long durationMs,
            double costUsd
    ) implements AgentRunResponse {}

    record Paused(
            String runId,
            String originalPrompt,
            List<PendingToolCall> pendingToolCalls,
            long iterations,
            long tokensTotal,
            long durationMs,
            double costUsd
    ) implements AgentRunResponse {}
}
