package dev.toleflaco.erp_purchasing_agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm.pricing")
public record LlmPricingProperties(
        double inputPerMtok,
        double outputPerMtok
) {
}
