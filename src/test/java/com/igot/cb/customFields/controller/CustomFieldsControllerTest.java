package com.igot.cb.customFields.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.customFields.service.CustomFieldsService;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.util.ApiResponse;
import com.igot.cb.pores.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

class CustomFieldsControllerTest {

    @Mock
    private CustomFieldsService customFieldsService;

    @InjectMocks
    private CustomFieldsController customFieldsController;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
    }

    private ApiResponse createMockResponse(HttpStatus status) {
        ApiResponse response = new ApiResponse();
        response.setResponseCode(status);
        response.setMessage("test");
        return response;
    }

    @Test
    void testCreateCustomFields() {
        JsonNode mockNode = objectMapper.createObjectNode();
        ApiResponse mockResponse = createMockResponse(HttpStatus.CREATED);

        when(customFieldsService.createCustomFields(any(), anyString())).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> responseEntity =
                customFieldsController.createCustomFields(mockNode, "token");

        assertEquals(HttpStatus.CREATED, responseEntity.getStatusCode());
        verify(customFieldsService).createCustomFields(mockNode, "token");
    }

    @Test
    void testReadCustomField() {
        ApiResponse mockResponse = createMockResponse(HttpStatus.OK);

        when(customFieldsService.readCustomField(anyString(), anyString())).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> responseEntity =
                customFieldsController.readCustomField("fieldId", "token");

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        verify(customFieldsService).readCustomField("fieldId", "token");
    }

    @Test
    void testUpdateCustomField() {
        JsonNode mockNode = objectMapper.createObjectNode();
        ApiResponse mockResponse = createMockResponse(HttpStatus.OK);

        when(customFieldsService.updateCustomField(any(), anyString())).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> responseEntity =
                customFieldsController.updateCustomField(mockNode, "token");

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        verify(customFieldsService).updateCustomField(mockNode, "token");
    }

    @Test
    void testDeleteCustomField() {
        ApiResponse mockResponse = createMockResponse(HttpStatus.NO_CONTENT);

        when(customFieldsService.deleteCustomField(anyString(), anyString())).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> responseEntity =
                customFieldsController.deleteCustomField("fieldId", "token");

        assertEquals(HttpStatus.NO_CONTENT, responseEntity.getStatusCode());
        verify(customFieldsService).deleteCustomField("fieldId", "token");
    }

    @Test
    void testSearchCustomFields() {
        SearchCriteria criteria = new SearchCriteria();
        ApiResponse mockResponse = createMockResponse(HttpStatus.OK);

        when(customFieldsService.searchCustomFields(any(),anyString(),anyString(),anyBoolean())).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> responseEntity =
                customFieldsController.searchCustomFields(criteria,"test-org","testAuthtoken");

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        verify(customFieldsService).searchCustomFields(criteria,"test-org","testAuthtoken",true);
    }

    @Test
    void testUploadCustomFieldHierarchy() {
        MultipartFile multipartFile = mock(MultipartFile.class);
        String metadata = "{}";
        ApiResponse mockResponse = createMockResponse(HttpStatus.OK);

        when(customFieldsService.uploadMasterListCustomField(any(), anyString(), anyString()))
                .thenReturn(mockResponse);

        ResponseEntity<ApiResponse> responseEntity =
                customFieldsController.uploadCustomFieldHierarchy(multipartFile, metadata, "token");

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        verify(customFieldsService).uploadMasterListCustomField(multipartFile, metadata, "token");
    }

    @Test
    void testUpdateMasterListCustomField() {
        MultipartFile file = mock(MultipartFile.class);
        String metadata = "{}";
        ApiResponse mockResponse = createMockResponse(HttpStatus.OK);

        when(customFieldsService.updateMasterListCustomField(any(), anyString(), anyString()))
                .thenReturn(mockResponse);

        ResponseEntity<ApiResponse> responseEntity =
                customFieldsController.updateMasterListCustomField(file, metadata, "token");

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        verify(customFieldsService).updateMasterListCustomField(file, metadata, "token");
    }

    @Test
    void testUpdateCustomFieldStatus() {
        JsonNode mockNode = objectMapper.createObjectNode();
        ApiResponse mockResponse = createMockResponse(HttpStatus.OK);

        when(customFieldsService.updateCustomFieldStatus(any(), anyString())).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> responseEntity =
                customFieldsController.updateCustomFieldStatus(mockNode, "token");

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        verify(customFieldsService).updateCustomFieldStatus(mockNode, "token");
    }

    @Test
    void testUpdatePopupStatus() {
        Map<String, Object> popupStatusData = Map.of(Constants.ORGANIZATION_ID, "org1",
                Constants.IS_POPUP_ENABLED, true);
        ApiResponse mockResponse = createMockResponse(HttpStatus.OK);

        when(customFieldsService.updatePopupStatus(anyMap(), anyString())).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> responseEntity =
                customFieldsController.updatePopupStatus(popupStatusData, "token");

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        verify(customFieldsService).updatePopupStatus(popupStatusData, "token");
    }
}
