package com.igot.cb.pores.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CacheServiceTest {

    @InjectMocks
    private CacheService cacheService;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() throws Exception {
        // Set TTL via reflection because @Value won't inject in test
        Field ttlField = CacheService.class.getDeclaredField("cacheTtl");
        ttlField.setAccessible(true);
        ttlField.set(cacheService, 3600L);
    }

    @Test
    void testPutCache_success() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("data");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        cacheService.putCache("key", new Object());

        verify(valueOperations).set("key", "data", 3600L, TimeUnit.SECONDS);
    }

    @Test
    void testPutCache_exception() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new RuntimeException("fail"));

        assertDoesNotThrow(() -> cacheService.putCache("key", new Object()),
                "Exception should be swallowed and not thrown");
    }


    @Test
    void testGetCache_success() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("key")).thenReturn("value");

        String value = cacheService.getCache("key");

        assertEquals("value", value);
    }

    @Test
    void testGetCache_exception() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis down"));

        String value = cacheService.getCache("key");

        assertNull(value); // Exception caught and null returned
    }

    @Test
    void testDeleteCache_success() {
        when(redisTemplate.delete("key")).thenReturn(true);

        Long result = cacheService.deleteCache("key");

        assertNull(result); // Method always returns null
    }

    @Test
    void testDeleteCache_notFound() {
        when(redisTemplate.delete("key")).thenReturn(false);

        Long result = cacheService.deleteCache("key");

        assertNull(result);
    }
}
