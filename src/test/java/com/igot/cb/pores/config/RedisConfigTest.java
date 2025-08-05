package com.igot.cb.pores.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@TestPropertySource(properties = {
        "spring.redis.cacheTtl=5000",
        "spring.redis.host=localhost",
        "spring.redis.port=6379",
        "spring.redis.data.host=localhost",
        "spring.redis.data.port=6380"
})
@ContextConfiguration(classes = {RedisConfig.class, RedisConfigTest.CacheManagerTestConfig.class})
class RedisConfigTest {

    @TestConfiguration
    static class CacheManagerTestConfig {
        @Bean
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }

    @Autowired
    private RedisConfig redisConfig;

    @Test
    void testRedisConnectionFactory() {
        RedisConnectionFactory factory = redisConfig.redisConnectionFactory();
        assertNotNull(factory);
        assertInstanceOf(LettuceConnectionFactory.class, factory);
    }


    @Test
    void testRedisTemplate() {
        RedisTemplate<String, String> template = redisConfig.redisTemplate(redisConfig.redisConnectionFactory());
        assertNotNull(template);
    }

}