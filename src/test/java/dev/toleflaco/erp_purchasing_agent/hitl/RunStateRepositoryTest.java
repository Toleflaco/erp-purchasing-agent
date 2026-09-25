package dev.toleflaco.erp_purchasing_agent.hitl;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class RunStateRepositoryTest {

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379);

    @Autowired
    RunStateRepository repository;

    @Autowired
    RedisTemplate<String,AgentRunSession> template;

    @Test
    void saveAndFindByIdReturnsSameSession() {

        // Given

        String runId = UUID.randomUUID().toString();
        AgentRunSession session = AgentRunSessionFixtures.sample(runId);

        // When
        repository.save(session, Duration.ofMinutes(4));
        Optional<AgentRunSession> result = repository.findById(runId);

        // Then
        assertTrue(result.isPresent());
        assertEquals(session, result.get());
    }

    @Test
    void findByIdReturnsEmptyForUnknownRunId() {

       // Given
        String runId = UUID.randomUUID().toString();

        // When
        Optional<AgentRunSession> result = repository.findById(runId);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void deleteByIdRemovesSession() {
        // Given
        String runId = UUID.randomUUID().toString();
        AgentRunSession session = AgentRunSessionFixtures.sample(runId);
        repository.save(session, Duration.ofMinutes(4));
        Optional<AgentRunSession> before = repository.findById(runId);
        assertTrue(before.isPresent());
        // When

        repository.deleteById(runId);
        Optional<AgentRunSession> after = repository.findById(runId);
        // Then
        assertTrue(after.isEmpty());
    }

    @Test
    void saveAssignsTtlToSession (){
        // Given
        String runId = UUID.randomUUID().toString();
        AgentRunSession session = AgentRunSessionFixtures.sample(runId);
        // When

        repository.save(session, Duration.ofMinutes(4));
        Long ttl = template.getExpire("hitl:session:" + runId, TimeUnit.SECONDS);
        // Then
        assertTrue(ttl > 0 && ttl <= 240,"TTL fuera de rango: " + ttl);
    }

}
