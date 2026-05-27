package com.igot.cb.pores.util;

import org.igot.common.ApiRespParam;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApiRespParamTest {

    @Test
    void testDefaultConstructorAndSettersGetters() {
        ApiRespParam param = new ApiRespParam();

        param.setResMsgId("res123");
        param.setMsgId("msg123");
        param.setErr("ERR_01");
        param.setStatus("FAILED");
        param.setErrMsg("Some error occurred");

        assertEquals("res123", param.getResMsgId());
        assertEquals("msg123", param.getMsgId());
        assertEquals("ERR_01", param.getErr());
        assertEquals("FAILED", param.getStatus());
        assertEquals("Some error occurred", param.getErrMsg());
    }

    @Test
    void testParameterizedConstructor() {
        ApiRespParam param = new ApiRespParam("id123");

        assertEquals("id123", param.getResMsgId());
        assertEquals("id123", param.getMsgId());

        // Other fields should be null
        assertNull(param.getErr());
        assertNull(param.getStatus());
        assertNull(param.getErrMsg());
    }
}
