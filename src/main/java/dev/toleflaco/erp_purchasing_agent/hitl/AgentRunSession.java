package dev.toleflaco.erp_purchasing_agent.hitl;

import java.util.List;

public record AgentRunSession(
        String runId,
        long usefulDurationMs,
        long totalTokens,
        double costUsd,
        long iterations,
        List<AgentMessage> conversationHistory
) {}
