package dev.toleflaco.erp_purchasing_agent.hitl;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


public class MessageMapperTest {

    private static final String SYSTEM_TEXT = "You are a helpful assistant";
    private static final String USER_TEXT = "You are a user";
    private static final String ASSISTANT_TEXT = "You are a assistant";

    private MessageMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new MessageMapper();
    }


    @Test
    public void roundTripSystemMessage() {

        // Given
        AgentMessage agentMessage = new AgentMessage(AgentMessage.Role.SYSTEM, SYSTEM_TEXT, List.of(), List.of());

        // When
        List<AgentMessage> result = mapper.toAgent(mapper.toSpringAi(List.of(agentMessage)));

        // Then
        assertThat(result).isEqualTo(List.of(agentMessage));
    }

    @Test
    public void roundTripUserMessage() {
        // Given
        AgentMessage agentMessage = new AgentMessage(AgentMessage.Role.USER, USER_TEXT, List.of(), List.of());

        // When
        List<AgentMessage> result = mapper.toAgent(mapper.toSpringAi(List.of(agentMessage)));

        // Then
        assertThat(result).isEqualTo(List.of(agentMessage));
    }

    @Test
    public void roundTripAssistantMessageWithToolCalls() {
        // Given
        AgentMessage agentMessage = new AgentMessage(AgentMessage.Role.ASSISTANT,
                ASSISTANT_TEXT,
                List.of(
                        new AgentMessage.AgentToolCall("id1", "name1", "{\"key\":\"value\"}"),
                        new AgentMessage.AgentToolCall("id2", "name2", "{\"key2\":\"value2\"}"),
                        new AgentMessage.AgentToolCall("id3", "name3", "{\"key3\":\"value3\"}")
                ),
                List.of());
        // When
        List<AgentMessage> result = mapper.toAgent(mapper.toSpringAi(List.of(agentMessage)));

        // Then
        assertThat(result).isEqualTo(List.of(agentMessage));

    }

    @Test
    public void roundTripToolMessageWithToolResponses() {
        // Given
        AgentMessage agentMessage = new AgentMessage(AgentMessage.Role.TOOL,
                null,
                List.of(),
                List.of(
                   new AgentMessage.AgentToolResponse("id1","name1","responseData1"),
                        new AgentMessage.AgentToolResponse("id2","name2","responseData2"),
                        new AgentMessage.AgentToolResponse("id3","name3","responseData3")
                ));
        // When
        List<AgentMessage> result = mapper.toAgent(mapper.toSpringAi(List.of(agentMessage)));

        // Then
        assertThat(result).isEqualTo(List.of(agentMessage));
    }
}
