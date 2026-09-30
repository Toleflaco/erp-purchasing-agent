package dev.toleflaco.erp_purchasing_agent.hitl;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, AgentRunSession> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, AgentRunSession> template = new RedisTemplate<>();
        JacksonJsonRedisSerializer<AgentRunSession> jsonSerializer = new JacksonJsonRedisSerializer<>(AgentRunSession.class);
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonSerializer);
        return template;
    }
}
