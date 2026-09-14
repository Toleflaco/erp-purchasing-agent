package dev.toleflaco.erp_purchasing_agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent.guardrails")
public record AgentGuardrailsProperties(
        long maxIterations,
        long maxTokensBudget,
        long maxDurationMs
) {
}
