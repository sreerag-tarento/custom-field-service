package com.igot.cb.pores.util;

import org.igot.common.ApiRespParam;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ApiResponseTest {

    @Test
    void testDefaultConstructor() {
        ApiResponse response = new ApiResponse();

        assertEquals("v1", response.getVer());
        assertNotNull(response.getTs());
        assertNotNull(response.getParams());
        assertNull(response.getId());
    }

    @Test
    void testParameterizedConstructor() {
        String id = "123";
        ApiResponse response = new ApiResponse(id);

        assertEquals("v1", response.getVer());
        assertNotNull(response.getTs());
        assertNotNull(response.getParams());
        assertEquals(id, response.getId());
    }

    @Test
    void testGettersAndSetters() {
        ApiResponse response = new ApiResponse();

        String id = "id123";
        String ver = "v2";
        String ts = new Timestamp(System.currentTimeMillis()).toString();
        String message = "Success";
        HttpStatus httpStatus = HttpStatus.OK;
        ApiRespParam params = new ApiRespParam("resp123");

        response.setId(id);
        response.setVer(ver);
        response.setTs(ts);
        response.setMessage(message);
        response.setResponseCode(httpStatus);
        response.setParams(params);

        assertEquals(id, response.getId());
        assertEquals(ver, response.getVer());
        assertEquals(ts, response.getTs());
        assertEquals(message, response.getMessage());
        assertEquals(httpStatus, response.getResponseCode());
        assertEquals(params, response.getParams());
    }

    @Test
    void testPutGetContainsKeyAndPutAll() {
        ApiResponse response = new ApiResponse();

        // Put single key-value
        response.put("key1", "value1");
        assertEquals("value1", response.get("key1"));
        assertTrue(response.containsKey("key1"));

        // Put all map
        Map<String, Object> map = new HashMap<>();
        map.put("key2", 200);
        map.put("key3", true);
        response.putAll(map);

        assertEquals(200, response.get("key2"));
        assertEquals(true, response.get("key3"));
        assertTrue(response.containsKey("key2"));
        assertTrue(response.containsKey("key3"));
    }

    @Test
    void testSetAndGetResultMap() {
        ApiResponse response = new ApiResponse();
        Map<String, Object> newMap = new HashMap<>();
        newMap.put("hello", "world");

        response.setResult(newMap);

        assertEquals("world", response.getResult().get("hello"));
        assertEquals(newMap, response.getResult());
    }
}
