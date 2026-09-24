package dev.toleflaco.erp_purchasing_agent.hitl;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class RunStateRepository {

    private final RedisTemplate<String, AgentRunSession> redisTemplate;

    public RunStateRepository(RedisTemplate<String, AgentRunSession> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void save(AgentRunSession session, Duration ttl) {
        redisTemplate.opsForValue().set(key(session.runId()), session, ttl);
    }

    public void deleteById(String runId) {
        redisTemplate.delete(key(runId));
    }

    public Optional<AgentRunSession> findById(String runId) {
        AgentRunSession session = redisTemplate.opsForValue().get(key(runId));
        return Optional.ofNullable(session);
    }

    private String key(String runId) {
        return "hitl:session:" + runId;
    }
}
