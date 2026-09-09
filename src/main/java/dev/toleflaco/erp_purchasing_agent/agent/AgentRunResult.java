package dev.toleflaco.erp_purchasing_agent.agent;

public record AgentRunResult(
        String text,
        long iterations,
        long tokensTotal,
        long durationMs,
        double costUsd
) {}
