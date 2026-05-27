package com.igot.cb.pores.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.igot.common.CustomException;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PayloadValidationTest {

    @Mock
    private JsonSchemaCache schemaCache;

    @Mock
    private JsonSchema jsonSchema;

    @InjectMocks
    private PayloadValidation payloadValidation;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
    }

    @Test
    void testValidatePayload_success_singleObject() throws Exception {
        String schemaKey = "testSchema";
        String json = "{\"name\": \"Alice\", \"age\": 30}";
        JsonNode payload = objectMapper.readTree(json);

        when(schemaCache.getSchema(schemaKey)).thenReturn(jsonSchema);
        when(jsonSchema.validate(payload)).thenReturn(Collections.emptySet());

        assertDoesNotThrow(() -> payloadValidation.validatePayload(schemaKey, payload));
    }

    @Test
    void testValidatePayload_success_array() throws Exception {
        String schemaKey = "testSchema";
        String json = "[{\"name\": \"Alice\", \"age\": 30}, {\"name\": \"Bob\", \"age\": 25}]";
        JsonNode payload = objectMapper.readTree(json);

        when(schemaCache.getSchema(schemaKey)).thenReturn(jsonSchema);
        when(jsonSchema.validate(any(JsonNode.class))).thenReturn(Collections.emptySet());

        assertDoesNotThrow(() -> payloadValidation.validatePayload(schemaKey, payload));
    }

    @Test
    void testValidatePayload_schemaNotFound() throws Exception {
        String schemaKey = "missingSchema";
        String json = "{\"name\": \"Charlie\"}";
        JsonNode payload = objectMapper.readTree(json);

        when(schemaCache.getSchema(schemaKey)).thenReturn(null);

        CustomException ex = assertThrows(CustomException.class,
                () -> payloadValidation.validatePayload(schemaKey, payload));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatusCode());
        assertTrue(ex.getMessage().contains("Schema not found for key"));
    }

    @Test
    void testValidatePayload_validationErrors() throws Exception {
        String schemaKey = "testSchema";
        String json = "{\"name\": \"\", \"age\": -1}";
        JsonNode payload = objectMapper.readTree(json);

        ValidationMessage message1 = mock(ValidationMessage.class);
        ValidationMessage message2 = mock(ValidationMessage.class);
        when(message1.getMessage()).thenReturn("Name is required");
        when(message2.getMessage()).thenReturn("Age must be positive");

        Set<ValidationMessage> messages = new HashSet<>(Arrays.asList(message1, message2));

        when(schemaCache.getSchema(schemaKey)).thenReturn(jsonSchema);
        when(jsonSchema.validate(payload)).thenReturn(messages);

        CustomException ex = assertThrows(CustomException.class,
                () -> payloadValidation.validatePayload(schemaKey, payload));

        assertTrue(ex.getMessage().contains("Name is required"));
    }

    @Test
    void testValidatePayload_exceptionCaught() throws Exception {
        String schemaKey = "testSchema";
        String json = "{\"name\": \"Alice\"}";
        JsonNode payload = objectMapper.readTree(json);

        when(schemaCache.getSchema(schemaKey)).thenThrow(new RuntimeException("Boom!"));

        CustomException ex = assertThrows(CustomException.class,
                () -> payloadValidation.validatePayload(schemaKey, payload));

        assertEquals("Boom!", ex.getMessage());
    }
}
