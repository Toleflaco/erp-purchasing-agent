package dev.toleflaco.erp_purchasing_agent.controller;

import dev.toleflaco.erp_purchasing_agent.agent.ReActAgent;
import dev.toleflaco.erp_purchasing_agent.dto.AgentRunRequest;
import dev.toleflaco.erp_purchasing_agent.dto.AgentRunResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent")
public class AgentController {

    private final ReActAgent reActAgent;

    public AgentController(ReActAgent reActAgent) {
        this.reActAgent = reActAgent;

    }

    @PostMapping("/run")
    public AgentRunResponse run(@RequestBody AgentRunRequest request) {
        return new AgentRunResponse(reActAgent.run(request.prompt()));
    }

}
