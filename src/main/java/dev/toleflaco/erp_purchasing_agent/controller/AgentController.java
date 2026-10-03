package dev.toleflaco.erp_purchasing_agent.controller;

import com.anthropic.models.beta.sessions.SessionCreateParams;
import dev.toleflaco.erp_purchasing_agent.agent.AgentRunResult;
import dev.toleflaco.erp_purchasing_agent.agent.ReActAgent;
import dev.toleflaco.erp_purchasing_agent.dto.AgentRunRequest;
import dev.toleflaco.erp_purchasing_agent.dto.AgentRunResponse;
import dev.toleflaco.erp_purchasing_agent.exception.GuardrailExceededException;
import dev.toleflaco.erp_purchasing_agent.exception.RunSessionNotFoundException;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);
    private final ReActAgent reActAgent;

    public AgentController(ReActAgent reActAgent) {
        this.reActAgent = reActAgent;

    }

    @PostMapping("/run")
    public ResponseEntity<AgentRunResponse> run(@RequestBody AgentRunRequest request) {
        AgentRunResult result = reActAgent.run(request.prompt());
        return switch (result) {
            case AgentRunResult.Completed c -> ResponseEntity.ok(new AgentRunResponse.Completed(
                    c.text(), c.iterations(), c.tokensTotal(), c.durationMs(), c.costUsd()
            ));
            case AgentRunResult.Paused p ->
                    ResponseEntity.accepted().body(new AgentRunResponse.Paused(
                            p.runId(),
                            request.prompt(),
                            p.pendingToolCalls(),
                            p.iterations(),
                            p.tokensTotal(),
                            p.durationMs(),
                            p.costUsd()
                    ));
        };
    }

    @PostMapping("/run/{runId}/approve")
    public ResponseEntity<AgentRunResponse> approve (@PathVariable String runId) {
        AgentRunResult result = reActAgent.resume(runId);
        return switch(result) {
            case AgentRunResult.Completed c -> ResponseEntity.ok(new AgentRunResponse.Completed(
                    c.text(),c.iterations(),c.tokensTotal(),c.durationMs(),c.costUsd()

            ));
            case AgentRunResult.Paused p -> ResponseEntity.accepted().body(new AgentRunResponse.Paused(
                    p.runId(),
                    p.originalPrompt(),
                    p.pendingToolCalls(),
                    p.iterations(),
                    p.tokensTotal(),
                    p.durationMs(),
                    p.costUsd()
            ));
        };
    }



    @ExceptionHandler(GuardrailExceededException.class)
    public ProblemDetail handleGuardrailExceeded(GuardrailExceededException ex) {
        log.warn("guardrail exception handled type={} value={} limit={}", ex.getType(), ex.getValue(), ex.getLimit());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
        pd.setTitle("Guardrail exceeded");
        pd.setProperty("guardrailType", ex.getType());
        pd.setProperty("value", ex.getValue());
        pd.setProperty("limit", ex.getLimit());
        return pd;
    }

    @ExceptionHandler(RunSessionNotFoundException.class)
    public ProblemDetail handleRunSessionNotFound (RunSessionNotFoundException ex) {
        log.warn("run session not found runId={}", ex.getRunId());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.GONE, ex.getMessage());
        pd.setTitle("Run session not found");
        pd.setProperty("run_id",ex.getRunId());
        return pd;
    }

}
