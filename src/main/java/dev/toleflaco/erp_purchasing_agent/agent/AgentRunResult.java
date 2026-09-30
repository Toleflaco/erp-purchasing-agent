package dev.toleflaco.erp_purchasing_agent.agent;

import dev.toleflaco.erp_purchasing_agent.hitl.PendingToolCall;

import java.util.List;

public sealed interface AgentRunResult
        permits AgentRunResult.Completed, AgentRunResult.Paused {

        record Completed(
                String text,
                long iterations,
                long tokensTotal,
                long durationMs,
                double costUsd
        ) implements AgentRunResult {}

        record Paused(
                String runId,
                List<PendingToolCall> pendingToolCalls,
                long iterations,
                long tokensTotal,
                long durationMs,
                double costUsd
        ) implements AgentRunResult {}
}
