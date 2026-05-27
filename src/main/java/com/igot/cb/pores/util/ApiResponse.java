package com.igot.cb.pores.util;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.igot.common.ApiRespParam;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;

/**
 * @author Shankaragouda
 */
@Getter
@Setter
public class ApiResponse {
    private String id;
    private String ver;
    private String ts;
    private String message;
    private ApiRespParam params;
    private HttpStatus responseCode;

    @JsonIgnore
    private transient Map<String, Object> response = new HashMap<>();

    public ApiResponse() {
        this.ver = "v1";
        this.ts = new Timestamp(System.currentTimeMillis()).toString();
        this.params = new ApiRespParam(UUID.randomUUID().toString());
    }

    public ApiResponse(String id) {
        this();
        this.id = id;
    }

    public Object get(String key) {
        return response.get(key);
    }

    public void put(String key, Object vo) {
        response.put(key, vo);
    }

    public void putAll(Map<String, Object> map) {
        response.putAll(map);
    }

    public boolean containsKey(String key) {
        return response.containsKey(key);
    }

    public Map<String, Object> getResult() {
        return response;
    }

    public void setResult(Map<String, Object> result) {
        response = result;
    }
}