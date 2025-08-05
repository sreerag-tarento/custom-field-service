package com.igot.cb.pores.exceptions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RestExceptionHandlingTest {

    private RestExceptionHandling restExceptionHandling;

    @BeforeEach
    void setUp() {
        restExceptionHandling = new RestExceptionHandling();
    }

    @Test
    void testHandleGenericException() {
        Exception ex = new Exception("Something went wrong");

        ResponseEntity<?> response = restExceptionHandling.handleException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ErrorResponse body = (ErrorResponse) response.getBody();
        assertNotNull(body);
        assertEquals("ERROR", body.getCode());
        assertEquals("Something went wrong", body.getMessage());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), body.getHttpStatusCode());
    }

    @Test
    void testHandleCustomException_withValidHttpStatus() {
        CustomException ex = new CustomException("BAD_REQ", "Bad request", HttpStatus.BAD_REQUEST);

        ResponseEntity<?> response = restExceptionHandling.handleException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ErrorResponse body = (ErrorResponse) response.getBody();
        assertNotNull(body);
        assertEquals("BAD_REQ", body.getCode());
        assertEquals("Bad request", body.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST.value(), body.getHttpStatusCode());
    }

    @Test
    void testHandleCustomException_withNullHttpStatus() {
        CustomException ex = new CustomException("MISSING_STATUS", "Missing status", null);

        assertThrows(NullPointerException.class, () -> {
            restExceptionHandling.handleException(ex);
        });
    }

    @Test
    void testHandleCustomException_withEmptyMessage() {
        CustomException ex = new CustomException("EMPTY_MSG", "", HttpStatus.BAD_REQUEST);

        ResponseEntity<?> response = restExceptionHandling.handleException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ErrorResponse body = (ErrorResponse) response.getBody();
        assertNotNull(body);
        assertEquals("EMPTY_MSG", body.getCode());
        assertEquals("", body.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST.value(), body.getHttpStatusCode());
    }

    @Test
    void testHandleCustomException_withExceptionInHttpStatus() {
        CustomException ex = new CustomException("ILLEGAL", "Illegal status", null) {
            @Override
            public HttpStatus getHttpStatusCode() {
                throw new IllegalArgumentException("Invalid HTTP status");
            }
        };

        assertThrows(IllegalArgumentException.class, () -> {
            restExceptionHandling.handleException(ex);
        });
    }

    @Test
    void testHandleCustomException_withBlankMessage() {
        CustomException ex = new CustomException("BLANK_MSG", "   ", HttpStatus.FORBIDDEN);

        ResponseEntity<?> response = restExceptionHandling.handleException(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        ErrorResponse body = (ErrorResponse) response.getBody();
        assertNotNull(body);
        assertEquals("BLANK_MSG", body.getCode());
        assertEquals("   ", body.getMessage());
        assertEquals(HttpStatus.FORBIDDEN.value(), body.getHttpStatusCode());
    }

    @Test
    void testHandleCustomException_withNonBlankMessage() {
        CustomException ex = new CustomException("VALID_MSG", "Valid message", HttpStatus.UNAUTHORIZED);

        ResponseEntity<?> response = restExceptionHandling.handleException(ex);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        ErrorResponse body = (ErrorResponse) response.getBody();
        assertNotNull(body);
        assertEquals("VALID_MSG", body.getCode());
        assertEquals("Valid message", body.getMessage());
        assertEquals(HttpStatus.UNAUTHORIZED.value(), body.getHttpStatusCode());
    }
}
