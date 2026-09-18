package dev.toleflaco.erp_purchasing_agent.hitl;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Set;

@ConfigurationProperties(prefix = "agent.hitl")
public record HitlProperties(
        Set<String> sensitiveTools,
        Duration ttl
) {}
