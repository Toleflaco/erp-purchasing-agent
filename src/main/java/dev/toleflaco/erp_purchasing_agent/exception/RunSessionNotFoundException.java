package dev.toleflaco.erp_purchasing_agent.exception;

public class RunSessionNotFoundException extends RuntimeException {

    private final String runId;    // <-- el campo

    public RunSessionNotFoundException(String runId) {
        super("Run session not found: " + runId);
        this.runId = runId;        // <-- asignacion al campo
    }

    public String getRunId() {
        return runId;              // <-- devuelve el campo
    }
}
