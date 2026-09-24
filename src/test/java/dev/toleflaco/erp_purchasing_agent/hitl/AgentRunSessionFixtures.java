package dev.toleflaco.erp_purchasing_agent.hitl;

import java.util.List;

public final class AgentRunSessionFixtures {

    private AgentRunSessionFixtures() {

    }

    public static AgentRunSession sample(String runId) {

        AgentMessage system = new AgentMessage(AgentMessage.Role.SYSTEM, "ejemplo SYSTEM", List.of(), List.of());
        AgentMessage user = new AgentMessage(AgentMessage.Role.USER, "ejemplo USER", List.of(), List.of());

        AgentMessage.AgentToolCall toolCall = new AgentMessage.AgentToolCall("call-1","review_order","{\"key\":\"value\"}");
        AgentMessage assistant = new AgentMessage(AgentMessage.Role.ASSISTANT,null,List.of(toolCall),List.of());

        AgentMessage.AgentToolResponse toolResponse = new AgentMessage.AgentToolResponse("call-1",
                "review_order",
                "{\"respuesta\":\"valor_respuesta\"}");
        AgentMessage tool = new AgentMessage(AgentMessage.Role.TOOL,null, List.of(),List.of(toolResponse));

        return new AgentRunSession(runId,1230,415,0.123,2,List.of(system,user,assistant,tool));
    }
}
