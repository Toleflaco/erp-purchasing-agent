package dev.toleflaco.erp_purchasing_agent.exception;

public class GuardrailExceededException extends RuntimeException {

    private final GuardrailType type;
    private final long value;
    private final long limit;

    public GuardrailExceededException(GuardrailType type, long value, long limit) {

        super(String.format("Guardrail exceeded: type=%s value=%d limit=%d", type, value, limit));

        this.type = type;
        this.value = value;
        this.limit = limit;
    }

    public GuardrailType getType() {
        return type;
    }

    public long getValue() {
        return value;
    }

    public long getLimit() {
        return limit;
    }

    public enum GuardrailType {
        ITERATIONS, TOKENS, DURATION
    }
}



