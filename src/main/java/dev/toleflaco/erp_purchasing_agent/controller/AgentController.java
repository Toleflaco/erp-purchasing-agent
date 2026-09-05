package dev.toleflaco.erp_purchasing_agent.controller;

import dev.toleflaco.erp_purchasing_agent.agent.ReActAgent;
import dev.toleflaco.erp_purchasing_agent.dto.AgentRunRequest;
import dev.toleflaco.erp_purchasing_agent.dto.AgentRunResponse;
import dev.toleflaco.erp_purchasing_agent.exception.GuardrailExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
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
    public AgentRunResponse run(@RequestBody AgentRunRequest request) {
        return new AgentRunResponse(reActAgent.run(request.prompt()));
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

}
