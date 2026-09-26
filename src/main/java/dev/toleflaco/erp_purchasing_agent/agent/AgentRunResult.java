package dev.toleflaco.erp_purchasing_agent.agent;
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
                String pendingToolName,
                long iterations,
                long tokensTotal,
                long durationMs,
                double costUsd
        ) implements AgentRunResult {}
}
