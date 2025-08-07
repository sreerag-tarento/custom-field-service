package com.igot.cb.customFields.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.customFields.entity.CustomFieldEntity;
import com.igot.cb.customFields.repository.CustomFieldRepository;
import com.igot.cb.pores.cache.CacheService;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.elasticsearch.dto.SearchResult;
import com.igot.cb.pores.elasticsearch.service.EsUtilService;
import com.igot.cb.pores.util.*;
import com.igot.cb.transactional.cassandrautils.CassandraOperationImpl;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

import static com.igot.cb.pores.util.Constants.IS_ENABLED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomFieldsServiceImplTest {

    @InjectMocks
    private CustomFieldsServiceImpl service;

    @Mock
    private CustomFieldRepository customFieldRepository;

    @Mock
    private PayloadValidation payloadValidation;

    @Mock
    private AccessTokenValidator accessTokenValidator;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private EsUtilService esUtilService;

    @Mock
    private CacheService cacheService;

    @Mock
    private CbServerProperties cbServerProperties;

    @Mock
    private CassandraOperationImpl cassandraOperation;

    @Mock
    private MultipartFile multipartFile;

    @Mock
    private Workbook workbook;

    @Mock
    private Sheet sheet;

    @Mock
    private Row headerRow;

    @Mock
    private Row dataRow;

    @Captor
    private ArgumentCaptor<CustomFieldEntity> entityCaptor;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCreateCustomFields_Success() {
        ObjectMapper realMapper = new ObjectMapper();
        ObjectNode jsonNode = realMapper.createObjectNode();
        jsonNode.put(Constants.NAME, "name");
        jsonNode.put(Constants.ATTRIBUTE_NAME, "attr");
        jsonNode.put(Constants.ORGANIZATION_ID, "org");
        jsonNode.put(Constants.IS_MANDATORY, true);

        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("userId");
        when(esUtilService.searchDocuments(any(), any(), any())).thenReturn(null);
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(new HashMap<>());
        when(objectMapper.valueToTree(any())).thenReturn(realMapper.createObjectNode());

        ApiResponse response = service.createCustomFields(jsonNode, "token");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
        assertEquals(Constants.SUCCESS, response.getMessage());

        verify(customFieldRepository).save(any(CustomFieldEntity.class));
        verify(esUtilService).addDocument(any(), any(), any(), any());
        verify(cacheService).putCache(any(), any());
    }

    @Test
    void testCreateCustomFields_InvalidToken() {
        ObjectNode jsonNode = new ObjectMapper().createObjectNode();
        jsonNode.put(Constants.NAME, "name");
        jsonNode.put(Constants.ATTRIBUTE_NAME, "attr");
        jsonNode.put(Constants.ORGANIZATION_ID, "org");

        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("");

        ApiResponse response = service.createCustomFields(jsonNode, "token");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(Constants.INVALID_AUTH_TOKEN, response.getParams().getErrMsg());

        verify(customFieldRepository, never()).save(any());
    }

    @Test
    void testCreateCustomFields_DuplicateAttributeName() {
        ObjectNode jsonNode = new ObjectMapper().createObjectNode();
        jsonNode.put(Constants.NAME, "name");
        jsonNode.put(Constants.ATTRIBUTE_NAME, "attr");
        jsonNode.put(Constants.ORGANIZATION_ID, "org");

        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("userId");

        SearchResult searchResult = new SearchResult();
        Map<String, Object> dataMap = Map.of(
                Constants.ORIGINAL_CUSTOM_FIELD_DATA,
                List.of(Map.of(Constants.ATTRIBUTE_NAME, "attr"))
        );
        searchResult.setData(List.of(dataMap));

        when(esUtilService.searchDocuments(any(), any(), any())).thenReturn(searchResult);

        ApiResponse response = service.createCustomFields(jsonNode, "token");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getMessage());
    }

    @Test
    void testCreateCustomFields_ExceptionThrown() {
        ObjectNode jsonNode = new ObjectMapper().createObjectNode();
        jsonNode.put(Constants.NAME, "name");
        jsonNode.put(Constants.ATTRIBUTE_NAME, "attr");
        jsonNode.put(Constants.ORGANIZATION_ID, "org");

        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("userId");
        when(esUtilService.searchDocuments(any(), any(), any())).thenReturn(null);

        ApiResponse response = service.createCustomFields(jsonNode, "token");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getMessage());
    }

    @Test
    void readCustomField_success_dbHit() {
        String customFieldId = "id";
        String token = "validToken";

        // Step 1: mock valid user
        when(accessTokenValidator.fetchUserIdFromAccessToken(token)).thenReturn("userId");

        CustomFieldEntity entity = mock(CustomFieldEntity.class);

        ObjectMapper realObjectMapper = new ObjectMapper();
        ObjectNode jsonNode = realObjectMapper.createObjectNode()
                .put("name", "SampleField")
                .put("type", "String");

        when(entity.getCustomFieldData()).thenReturn(jsonNode);

        when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue(customFieldId)).thenReturn(Optional.of(entity));

        // Step 4: ObjectMapper converts JsonNode to Map
        Map<String, Object> customFieldMap = Map.of(
                "name", "SampleField",
                "type", "String",
                Constants.CUSTOM_FIELD_ID, customFieldId
        );

        when(objectMapper.convertValue(jsonNode, Map.class)).thenReturn(new HashMap<>(customFieldMap));

        // Step 5: call method
        ApiResponse response = service.readCustomField(customFieldId, token);

        // Step 6: verify
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getMessage());
        assertNotNull(response.getResult());
        assertEquals(customFieldMap.get("name"), ((Map<?, ?>) response.getResult()).get("name"));

        verify(cacheService).putCache(eq("CUSTOM_FIELD_" + customFieldId), any());
    }

    @Test
    void readCustomField_notFound() {
        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("userId");
        when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("id")).thenReturn(Optional.empty());

        ApiResponse res = service.readCustomField("id", "token");

        assertEquals(HttpStatus.NOT_FOUND, res.getResponseCode());
        assertEquals(Constants.FAILED, res.getParams().getStatus());
        assertTrue(res.getParams().getErrMsg().contains("not found"));
    }

    @Test
    void readCustomField_invalidToken() {
        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("");

        ApiResponse res = service.readCustomField("id", "token");

        assertEquals(HttpStatus.UNAUTHORIZED, res.getResponseCode());
        assertEquals(Constants.FAILED, res.getParams().getStatus());
        assertEquals(Constants.INVALID_AUTH_TOKEN, res.getParams().getErrMsg());
    }

    @Test
    void readCustomField_exception() {
        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenThrow(new RuntimeException());

        ApiResponse res = service.readCustomField("id", "token");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, res.getResponseCode());
        assertEquals(Constants.FAILED, res.getMessage());
        assertEquals("FAILED_TO_READ_CUSTOM_FIELD", res.getParams().getErrMsg());
    }

    @Test
    void testInvalidToken() {
        when(accessTokenValidator.fetchUserIdFromAccessToken("badToken")).thenReturn("");
        ApiResponse response = service.updateCustomField(createSampleCustomFieldJson(), "badToken");
        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getMessage());
    }

    @Test
    void testFieldNotFound() {
        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("user");
        when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("cf123"))
                .thenReturn(Optional.empty());

        ApiResponse response = service.updateCustomField(createSampleCustomFieldJson(), "token");
        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
    }

    @Test
    void testDuplicateAttributeName() {
        CustomFieldEntity entity = mock(CustomFieldEntity.class);
        JsonNode original = createSampleCustomFieldJson();
        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("user");
        when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("cf123"))
                .thenReturn(Optional.of(entity));
        when(entity.getCustomFieldData()).thenReturn(original);
        SearchResult result = mock(SearchResult.class);
        when(result.getData()).thenReturn(List.of(Map.of(
                Constants.ORIGINAL_CUSTOM_FIELD_DATA,
                List.of(Map.of(Constants.ATTRIBUTE_NAME, "attr1"))
        )));
        when(esUtilService.searchDocuments(any(), any(), any())).thenReturn(result);

        ApiResponse response = service.updateCustomField(createSampleCustomFieldJson(), "token");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getMessage());
    }

    @Test
    void testOrganisationIdChanged() {
        CustomFieldEntity entity = mock(CustomFieldEntity.class);
        ObjectNode original = new ObjectMapper().createObjectNode();
        original.put(Constants.ORGANISATION_ID, "org-different");
        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("user");
        when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("cf123"))
                .thenReturn(Optional.of(entity));
        when(entity.getCustomFieldData()).thenReturn(original);
        when(esUtilService.searchDocuments(any(), any(), any())).thenReturn(new SearchResult());

        ApiResponse response = service.updateCustomField(createSampleCustomFieldJson(), "token");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testSuccessfulUpdate() throws Exception {
        CustomFieldEntity entity = new CustomFieldEntity();
        ObjectNode original = createSampleCustomFieldJson();
        original.put(Constants.ORGANISATION_ID, "org1");
        original.put(Constants.IS_MANDATORY, true);
        original.put(Constants.IS_ACTIVE, true);
        original.put(Constants.CREATED_ON, new Date().getTime());
        original.put(Constants.CREATED_BY, "jay");

        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("user");
        when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("cf123"))
                .thenReturn(Optional.of(entity));
        entity.setCustomFieldData(original);

        when(esUtilService.searchDocuments(any(), any(), any())).thenReturn(new SearchResult());
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(new HashMap<>());
        when(objectMapper.valueToTree(any())).thenReturn(original);

        ApiResponse response = service.updateCustomField(createSampleCustomFieldJson(), "token");
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getMessage());
    }

    @Test
    void testExceptionThrown() {
        when(accessTokenValidator.fetchUserIdFromAccessToken("token"))
                .thenThrow(new RuntimeException("unexpected error"));

        ApiResponse response = service.updateCustomField(createSampleCustomFieldJson(), "token");
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getMessage());
    }

    @Test
    void testInvalidToken_deleteCustomField() {
        when(accessTokenValidator.fetchUserIdFromAccessToken("badToken")).thenReturn("");

        ApiResponse response = service.deleteCustomField("cf123", "badToken");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getMessage());
    }


    @Test
    void testFieldEnabledRemoveOrgFails(){
        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("user");

        CustomFieldEntity entity = new CustomFieldEntity();
        ObjectNode enabledData = createSampleCustomFieldJson();
        enabledData.put(IS_ENABLED, true);

        entity.setCustomFieldData(enabledData);

        when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("cf123")).thenReturn(Optional.of(entity));
        doThrow(new RuntimeException("fail")).when(cassandraOperation).getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), any());

        ApiResponse response = service.deleteCustomField("cf123", "token");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getMessage());
    }

    @Test
    void testEnabledRemoveOrgFails() {
        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("user");

        CustomFieldEntity entity = new CustomFieldEntity();
        ObjectNode enabledData = createSampleCustomFieldJson();
        enabledData.put(IS_ENABLED, true);

        entity.setCustomFieldData(enabledData);

        when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("cf123")).thenReturn(Optional.of(entity));
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());

        ApiResponse response = service.deleteCustomField("cf123", "token");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getMessage());
    }

    @Test
    void testSuccessfulDelete() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("user");

        CustomFieldEntity entity = new CustomFieldEntity();
        ObjectNode dataNode = createSampleCustomFieldJson();

        entity.setCustomFieldData(dataNode);

        when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("cf123")).thenReturn(Optional.of(entity));
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(new HashMap<>());
        when(cbServerProperties.getCustomFieldEntity()).thenReturn("customField");
        when(cbServerProperties.getCustomFieldElasticMappingJsonPath()).thenReturn("mapping.json");

        ApiResponse response = service.deleteCustomField("cf123", "token");

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getMessage());
        assertEquals(Constants.DELETED, ((Map)response.getResult()).get(Constants.STATUS));
    }

    @Test
    void testDeleteCustomField_NotFound() {
        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("user");

        CustomFieldEntity entity = new CustomFieldEntity();
        ObjectNode dataNode = createSampleCustomFieldJson();

        entity.setCustomFieldData(dataNode);

        when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("cf123")).thenReturn(Optional.empty());
        ApiResponse response = service.deleteCustomField("cf123", "token");

        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getMessage());
    }

    @Test
    void testUnexpectedException() {
        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenThrow(new RuntimeException("unexpected"));
        ApiResponse response = service.deleteCustomField("cf123", "token");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getMessage());
    }

    @Test
    void testSearchWithIsActiveProvided() {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setFilterCriteriaMap(new HashMap<>());
        criteria.getFilterCriteriaMap().put(Constants.IS_ACTIVE, false);

        SearchResult result = new SearchResult();
        when(esUtilService.searchDocuments(any(), any(), any())).thenReturn(result);
        when(cbServerProperties.getCustomFieldEntity()).thenReturn("customField");
        when(cbServerProperties.getCustomFieldElasticMappingJsonPath()).thenReturn("mapping.json");


        ApiResponse response = service.searchCustomFields(criteria);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getMessage());
        assertNotNull(response.getResult().get(Constants.SEARCH_RESULTS));
    }

    @Test
    void testSearchWithoutIsActiveProvided() {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setFilterCriteriaMap(new HashMap<>());

        SearchResult result = new SearchResult();
        when(esUtilService.searchDocuments(any(), any(), any())).thenReturn(result);
        when(cbServerProperties.getCustomFieldEntity()).thenReturn("customField");
        when(cbServerProperties.getCustomFieldElasticMappingJsonPath()).thenReturn("mapping.json");

        ApiResponse response = service.searchCustomFields(criteria);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getMessage());
        assertTrue((Boolean) criteria.getFilterCriteriaMap().get(Constants.IS_ACTIVE));
        assertNotNull(response.getResult().get(Constants.SEARCH_RESULTS));
    }

    @Test
    void testSearchThrowsException() {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setFilterCriteriaMap(new HashMap<>());

        when(cbServerProperties.getCustomFieldEntity()).thenReturn("customField");
        when(cbServerProperties.getCustomFieldElasticMappingJsonPath()).thenReturn("mapping.json");

        when(esUtilService.searchDocuments(any(), any(), any())).thenThrow(new RuntimeException("fail"));

        ApiResponse response = service.searchCustomFields(criteria);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getMessage());
    }

    @Test
    void testInvalidJson() throws Exception {
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenThrow(new RuntimeException("bad json"));

        ApiResponse response = service.uploadMasterListCustomField(null, "bad-json", "token");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getMessage());
    }

    @Test
    void testInvalidToken_uploadMasterListCustomField() throws Exception {
        when(cbServerProperties.getCustomFieldListValidationFilePath()).thenReturn("validation.json");
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(createSampleJsonMap());
        when(objectMapper.valueToTree(any())).thenReturn(null);
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("");

        ApiResponse response = service.uploadMasterListCustomField(null, "{}", "bad-token");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
    }

    @Test
    void testEmptyFile() throws Exception {
        when(cbServerProperties.getCustomFieldListValidationFilePath()).thenReturn("validation.json");
        when(cbServerProperties.getCustomFieldEntity()).thenReturn("entity");
        when(cbServerProperties.getCustomFieldElasticMappingJsonPath()).thenReturn("mapping.json");
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(createSampleJsonMap());
        when(objectMapper.valueToTree(any())).thenReturn(null);
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user");
        when(esUtilService.searchDocuments(any(), any(), any())).thenReturn(new SearchResult());

        MultipartFile file = new MockMultipartFile("file", "", "", new byte[0]);

        ApiResponse response = service.uploadMasterListCustomField(file, "{}", "token");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testWrongFileType() throws Exception {
        when(cbServerProperties.getCustomFieldListValidationFilePath()).thenReturn("validation.json");
        when(cbServerProperties.getAllowedExtensions()).thenReturn(".xlsx");
        when(cbServerProperties.getAllowedContentTypes()).thenReturn("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        when(cbServerProperties.getCustomFieldEntity()).thenReturn("entity");
        when(cbServerProperties.getCustomFieldElasticMappingJsonPath()).thenReturn("mapping.json");

        MultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "abc".getBytes());

        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(createSampleJsonMap());
        when(objectMapper.valueToTree(any())).thenReturn(null);
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user");
        when(esUtilService.searchDocuments(any(), any(), any())).thenReturn(new SearchResult());

        ApiResponse response = service.uploadMasterListCustomField(file, "{}", "token");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testExcelHeaderMismatch() throws Exception {
        when(cbServerProperties.getCustomFieldListValidationFilePath()).thenReturn("validation.json");
        when(cbServerProperties.getAllowedExtensions()).thenReturn(".xlsx");
        when(cbServerProperties.getAllowedContentTypes()).thenReturn("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        when(cbServerProperties.getCustomFieldMaxLevel()).thenReturn(5);
        when(cbServerProperties.getCustomFieldEntity()).thenReturn("entity");
        when(cbServerProperties.getCustomFieldElasticMappingJsonPath()).thenReturn("mapping.json");

        MultipartFile file = validExcelFile(1, "wrongHeader");

        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(createSampleJsonMap());
        when(objectMapper.valueToTree(any())).thenReturn(null);
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user");
        when(esUtilService.searchDocuments(any(), any(), any())).thenReturn(new SearchResult());

        ApiResponse response = service.uploadMasterListCustomField(file, "{}", "token");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testExceptionReadingExcel() throws Exception {
        when(cbServerProperties.getCustomFieldListValidationFilePath()).thenReturn("validation.json");
        when(cbServerProperties.getAllowedExtensions()).thenReturn(".xlsx");
        when(cbServerProperties.getAllowedContentTypes()).thenReturn("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        when(cbServerProperties.getCustomFieldMaxLevel()).thenReturn(5);
        when(cbServerProperties.getCustomFieldEntity()).thenReturn("entity");
        when(cbServerProperties.getCustomFieldElasticMappingJsonPath()).thenReturn("mapping.json");

        MultipartFile file = mock(MultipartFile.class);
        when(file.getInputStream()).thenThrow(new RuntimeException("error"));

        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(createSampleJsonMap());
        when(objectMapper.valueToTree(any())).thenReturn(null);
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user");
        when(esUtilService.searchDocuments(any(), any(), any())).thenReturn(new SearchResult());
        when(file.getOriginalFilename()).thenReturn("test.xlsx");
        when(file.getContentType()).thenReturn("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        when(file.isEmpty()).thenReturn(false);

        ApiResponse response = service.uploadMasterListCustomField(file, "{}", "token");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testSuccessfulUpload() throws Exception {
        when(cbServerProperties.getCustomFieldListValidationFilePath()).thenReturn("validation.json");
        when(cbServerProperties.getAllowedExtensions()).thenReturn(".xlsx");
        when(cbServerProperties.getAllowedContentTypes()).thenReturn("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        when(cbServerProperties.getCustomFieldMaxLevel()).thenReturn(5);
        when(cbServerProperties.getCustomFieldEntity()).thenReturn("entity");
        when(cbServerProperties.getCustomFieldElasticMappingJsonPath()).thenReturn("mapping.json");

        MultipartFile file = validExcelFile(1);

        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(createSampleJsonMap());
        when(objectMapper.valueToTree(any())).thenReturn(mock(JsonNode.class));
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(new HashMap<>());
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user");
        when(esUtilService.searchDocuments(any(), any(), any())).thenReturn(new SearchResult());
        when(objectMapper.createObjectNode()).thenAnswer(invocation -> new ObjectMapper().createObjectNode());
        when(objectMapper.createArrayNode()).thenAnswer(invocation -> new ObjectMapper().createArrayNode());
        when(objectMapper.valueToTree(anyList())).thenAnswer(invocation -> new ObjectMapper().valueToTree(invocation.getArgument(0)));

        ApiResponse response = service.uploadMasterListCustomField(file, "{}", "token");

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
    }

    @Test
    void testUpdateMasterList_InternalServerError() throws Exception {
        String json = """
    {
      "customFieldId": "cf1",
      "organizationId": "org1",
      "customFieldData": [
        { "attributeName": "attr1", "name": "header", "level": 1 }
      ]
    }
    """;
        String token = "valid-token";

        ObjectMapper realMapper = new ObjectMapper();

        JsonNode payloadNode = realMapper.readTree(json);
        Map<String, Object> jsonMap = realMapper.convertValue(payloadNode, Map.class);

        // ObjectMapper behavior
        when(objectMapper.createObjectNode()).thenAnswer(inv -> realMapper.createObjectNode());
        when(objectMapper.createArrayNode()).thenAnswer(inv -> realMapper.createArrayNode());
        when(objectMapper.valueToTree(any())).thenAnswer(inv -> realMapper.valueToTree(inv.getArgument(0)));
        when(objectMapper.readValue(json, Map.class)).thenReturn(jsonMap);
        when(objectMapper.valueToTree(jsonMap)).thenReturn(payloadNode);

        // Access token mock
        when(accessTokenValidator.fetchUserIdFromAccessToken(token)).thenReturn("user1");

        // DB mock
        CustomFieldEntity entity = new CustomFieldEntity();
        ObjectNode existingData = realMapper.createObjectNode();
        existingData.put(IS_ENABLED, true);
        entity.setCustomFieldData(existingData);

        when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("cf1"))
                .thenReturn(Optional.of(entity));

        // Multipart file mock
        when(multipartFile.getOriginalFilename()).thenReturn("file.xlsx");
        when(multipartFile.getContentType()).thenReturn("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        when(multipartFile.isEmpty()).thenReturn(false);

        // Provide a VALID XLSX stream
        when(multipartFile.getInputStream()).thenReturn(
                new ByteArrayInputStream(generateValidExcelFileBytes())
        );

        // Server properties
        when(cbServerProperties.getAllowedExtensions()).thenReturn(".xlsx");
        when(cbServerProperties.getAllowedContentTypes()).thenReturn("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        when(cbServerProperties.getCustomFieldMaxLevel()).thenReturn(1);

        // Call the service
        ApiResponse response = service.updateMasterListCustomField(multipartFile, json, token);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals("Failed", response.getMessage());
    }


    @Test
    void testUpdateMasterList_Failed() throws Exception {
        String json = """
    {
      "customFieldId": "cf1",
      "organizationId": "org1",
      "customFieldData": [
        { "attributeName": "attr1", "name": "name1", "level": 1 }
      ]
    }
    """;
        String token = "valid-token";

        ObjectMapper realMapper = new ObjectMapper();

        // Mock a valid payload node with expected fields
        JsonNode payloadNode = realMapper.readTree(json);

        // Prepare jsonMap too if required
        Map<String, Object> jsonMap = realMapper.convertValue(payloadNode, Map.class);

        // ObjectMapper behavior
        when(objectMapper.valueToTree(any())).thenAnswer(inv -> realMapper.valueToTree(inv.getArgument(0)));
        when(objectMapper.readValue(json, Map.class)).thenReturn(jsonMap);
        when(objectMapper.valueToTree(jsonMap)).thenReturn(payloadNode);

        // Access token mock
        when(accessTokenValidator.fetchUserIdFromAccessToken(token)).thenReturn("user1");

        // DB mock
        CustomFieldEntity entity = new CustomFieldEntity();

        ObjectNode existingData = realMapper.createObjectNode();
        existingData.put(IS_ENABLED, true);

        entity.setCustomFieldData(existingData);

        when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("cf1"))
                .thenReturn(Optional.of(entity));

        // Multipart file mock
        when(multipartFile.getOriginalFilename()).thenReturn("file.xlsx");
        when(multipartFile.getContentType()).thenReturn("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        when(multipartFile.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(multipartFile.isEmpty()).thenReturn(false);

        // Server properties
        when(cbServerProperties.getAllowedExtensions()).thenReturn(".xlsx");
        when(cbServerProperties.getAllowedContentTypes()).thenReturn("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        when(cbServerProperties.getCustomFieldMaxLevel()).thenReturn(1);

        // When a new workbook is constructed on InputStream
        when(multipartFile.getInputStream()).thenReturn(getClass().getResourceAsStream("/dummy.xlsx"));

        // Call the service
        ApiResponse response = service.updateMasterListCustomField(multipartFile, json, token);

        // Assertions
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Failed", response.getMessage());
    }

    @Test
    void testUpdateMasterList_Success() throws Exception {
        String json = """
    {
      "customFieldId": "cf1",
      "organizationId": "org1",
      "customFieldData": [
        { "attributeName": "attr1", "name": "header", "level": 1 }
      ]
    }
    """;
        String token = "valid-token";

        ObjectMapper realMapper = new ObjectMapper();

        JsonNode payloadNode = realMapper.readTree(json);
        Map<String, Object> jsonMap = realMapper.convertValue(payloadNode, Map.class);

        // ObjectMapper behavior
        when(objectMapper.createObjectNode()).thenAnswer(inv -> realMapper.createObjectNode());
        when(objectMapper.createArrayNode()).thenAnswer(inv -> realMapper.createArrayNode());
        when(objectMapper.valueToTree(any())).thenAnswer(inv -> realMapper.valueToTree(inv.getArgument(0)));
        when(objectMapper.convertValue(any(), eq(Map.class))).thenAnswer(inv -> realMapper.convertValue(inv.getArgument(0), Map.class));
        when(objectMapper.readValue(json, Map.class)).thenReturn(jsonMap);
        when(objectMapper.valueToTree(jsonMap)).thenReturn(payloadNode);

        // Access token mock
        when(accessTokenValidator.fetchUserIdFromAccessToken(token)).thenReturn("user1");

        // DB mock
        CustomFieldEntity entity = new CustomFieldEntity();
        ObjectNode existingData = realMapper.createObjectNode();
        existingData.put(IS_ENABLED, true);
        existingData.put("organisationId","124526");
        existingData.put("createdBy","Ajay");
        existingData.put("createdOn", "2023-12-12");
        entity.setCustomFieldData(existingData);

        when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("cf1"))
                .thenReturn(Optional.of(entity));

        // Multipart file mock
        when(multipartFile.getOriginalFilename()).thenReturn("file.xlsx");
        when(multipartFile.getContentType()).thenReturn("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        when(multipartFile.isEmpty()).thenReturn(false);

        // Provide a VALID XLSX stream
        when(multipartFile.getInputStream()).thenReturn(
                new ByteArrayInputStream(generateValidExcelFileBytes())
        );

        // Server properties
        when(cbServerProperties.getAllowedExtensions()).thenReturn(".xlsx");
        when(cbServerProperties.getAllowedContentTypes()).thenReturn("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        when(cbServerProperties.getCustomFieldMaxLevel()).thenReturn(1);

        // Cache, ES mocks
        Mockito.doAnswer(invocation -> null)
                .when(cacheService).putCache(anyString(), any(JsonNode.class));
        when(esUtilService.updateDocument(any(), any(), any(), any())).thenReturn("");

        // Call the service
        ApiResponse response = service.updateMasterListCustomField(multipartFile, json, token);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals("success", response.getMessage());
    }

    @Test
    void testUpdateMasterList_InvalidToken() throws Exception {
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(new HashMap<>());
        when(objectMapper.valueToTree(any())).thenReturn(new ObjectMapper().createObjectNode());
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("");

        ApiResponse response = service.updateMasterListCustomField(multipartFile, "{}", "bad-token");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
    }

    @Test
    void testUpdateMasterList_Exception() {
        String json = """
    {
      "customFieldId": "cf1",
      "organizationId": "org1",
      "customFieldData": [
        { "attributeName": "attr1", "name": "header", "level": 1 }
      ]
    }
    """; // deliberately broken
        String token = "valid-token";

        when(accessTokenValidator.fetchUserIdFromAccessToken(token)).thenReturn("user1");

        // Call the service
        ApiResponse response = service.updateMasterListCustomField(multipartFile, json, token);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
        assertTrue(response.getParams().getErr().contains("Failed to update master list custom field"));  // adjust message check as per your impl
    }

    @Test
    void testUpdateMasterList_ShouldReturn_404() throws Exception {
        String json = """
    {
      "customFieldId": "cf1",
      "organizationId": "org1",
      "customFieldData": [
        { "attributeName": "attr1", "name": "header", "level": 1 }
      ]
    }
    """;
        String token = "valid-token";

        ObjectMapper realMapper = new ObjectMapper();

        JsonNode payloadNode = realMapper.readTree(json);
        Map<String, Object> jsonMap = realMapper.convertValue(payloadNode, Map.class);

        // ObjectMapper behavior
        when(objectMapper.valueToTree(any())).thenAnswer(inv -> realMapper.valueToTree(inv.getArgument(0)));
        when(objectMapper.readValue(json, Map.class)).thenReturn(jsonMap);
        when(objectMapper.valueToTree(jsonMap)).thenReturn(payloadNode);

        // Access token mock
        when(accessTokenValidator.fetchUserIdFromAccessToken(token)).thenReturn("user1");

        when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("cf1"))
                .thenReturn(Optional.empty());

        // Call the service
        ApiResponse response = service.updateMasterListCustomField(multipartFile, json, token);

        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getMessage());
    }


    @Test
    void testUpdateMasterListCustomField_whenJsonParsingFails_shouldReturnErrorResponse() throws Exception {
        // Arrange
        String invalidJson = "{invalid_json}";
        MockMultipartFile mockFile = new MockMultipartFile("file", "data.csv", "text/csv", "test".getBytes());

        // Simulate exception when parsing JSON
        when(objectMapper.readValue(invalidJson, Map.class)).thenThrow(new JsonProcessingException("Unexpected character") {});

        // Act
        ApiResponse response = service.updateMasterListCustomField(mockFile, invalidJson, "Bearer dummy-token");

        // Assert
        assertNotNull(response);
        assertEquals(Constants.FAILED, response.getMessage());
        assertTrue(response.getParams().getErr().contains(Constants.INVALID_JSON_CUSTOM_FIELDS_MASTER_DATA));
    }

    @Test
    void testUpdateCustomFieldStatus_success() throws Exception {
        ObjectMapper realMapper = new ObjectMapper();
        ObjectNode requestNode = realMapper.createObjectNode();
        requestNode.put(Constants.CUSTOM_FIELD_ID, "cf1");
        requestNode.put(Constants.IS_ENABLED, true);

        ObjectNode customFieldData = realMapper.createObjectNode();
        customFieldData.put(Constants.IS_ENABLED, false);
        customFieldData.put(Constants.ORGANIZATION_ID, "org1");

        CustomFieldEntity entity = new CustomFieldEntity();
        entity.setCustomFieldData(customFieldData);

        when(cbServerProperties.getCustomFieldStatusUpdateValidationFilePath()).thenReturn("validation.json");
        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("user1");
        when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("cf1")).thenReturn(Optional.of(entity));
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(new HashMap<>());
        when(cbServerProperties.getCustomFieldEntity()).thenReturn("entity");
        when(cbServerProperties.getCustomFieldElasticMappingJsonPath()).thenReturn("mapping.json");

        ApiResponse response = service.updateCustomFieldStatus(requestNode, "token");

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
    }

    @Test
    void testUpdateCustomFieldStatus_success_1() throws Exception {
        ObjectMapper realMapper = new ObjectMapper();

        // Prepare request
        ObjectNode requestNode = realMapper.createObjectNode();
        requestNode.put(Constants.CUSTOM_FIELD_ID, "cf1");
        requestNode.put(Constants.IS_ENABLED, true);

        // Prepare customFieldData node
        ObjectNode customFieldData = realMapper.createObjectNode();
        customFieldData.put(Constants.IS_ENABLED, false);
        customFieldData.put(Constants.ORGANIZATION_ID, "org1");
        customFieldData.put(Constants.TYPE, "masterList");
        customFieldData.put(Constants.LEVELS, 5);

        // Prepare entity
        CustomFieldEntity entity = new CustomFieldEntity();
        entity.setCustomFieldData(customFieldData);

        // Mock org data from Cassandra
        String customFieldDataJson = "{\"customFieldIds\": [\"field-1\"], \"customFieldsCount\": 1}";
        Map<String, Object> orgMap = new HashMap<>();
        orgMap.put(Constants.CUSTOM_FIELDS_DATA, customFieldDataJson);
        List<Map<String, Object>> orgList = new ArrayList<>();
        orgList.add(orgMap);

        // Setup mocks
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.ORG_TABLE),
                anyMap(),
                eq(Collections.singletonList(Constants.CUSTOM_FIELDS_DATA)),
                isNull()
        )).thenReturn(orgList);

        when(cbServerProperties.getCustomFieldMaxAllowedCount()).thenReturn(10);
        when(cbServerProperties.getCustomFieldStatusUpdateValidationFilePath()).thenReturn("validation.json");

        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("user1");

        when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("cf1"))
                .thenReturn(Optional.of(entity));

        when(objectMapper.readValue(anyString(), eq(Map.class))).thenAnswer(invocation ->
                realMapper.readValue(invocation.getArgument(0, String.class), Map.class));
        // Act
        ApiResponse response = service.updateCustomFieldStatus(requestNode, "token");

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
    }


    @Test
    void testUpdateCustomFieldStatus_Return400() {
        ObjectMapper realMapper = new ObjectMapper();
        ObjectNode requestNode = realMapper.createObjectNode();
        requestNode.put(Constants.CUSTOM_FIELD_ID, "");
        requestNode.put(Constants.IS_ENABLED, true);

        ObjectNode customFieldData = realMapper.createObjectNode();
        customFieldData.put(Constants.IS_ENABLED, false);
        customFieldData.put(Constants.ORGANIZATION_ID, "org1");

        when(cbServerProperties.getCustomFieldStatusUpdateValidationFilePath()).thenReturn("validation.json");
        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("user1");

        ApiResponse response = service.updateCustomFieldStatus(requestNode, "token");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getMessage());
    }

    @Test
    void testUpdateCustomFieldStatus_invalidToken() {
        ObjectNode requestNode = new ObjectMapper().createObjectNode();
        requestNode.put(Constants.CUSTOM_FIELD_ID, "cf1");
        requestNode.put(Constants.IS_ENABLED, true);

        when(cbServerProperties.getCustomFieldStatusUpdateValidationFilePath()).thenReturn("validation.json");
        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("");

        ApiResponse response = service.updateCustomFieldStatus(requestNode, "token");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
    }

    @Test
    void testUpdateCustomFieldStatus_customFieldNotFound() {
        ObjectNode requestNode = new ObjectMapper().createObjectNode();
        requestNode.put(Constants.CUSTOM_FIELD_ID, "cf1");
        requestNode.put(Constants.IS_ENABLED, true);

        when(cbServerProperties.getCustomFieldStatusUpdateValidationFilePath()).thenReturn("validation.json");
        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("user1");
        when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("cf1")).thenReturn(Optional.empty());

        ApiResponse response = service.updateCustomFieldStatus(requestNode, "token");

        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
    }

    @Test
    void testUpdateCustomFieldStatus_statusAlreadySet() {
        ObjectMapper realMapper = new ObjectMapper();
        ObjectNode requestNode = realMapper.createObjectNode();
        requestNode.put(Constants.CUSTOM_FIELD_ID, "cf1");
        requestNode.put(Constants.IS_ENABLED, true);

        ObjectNode customFieldData = realMapper.createObjectNode();
        customFieldData.put(Constants.IS_ENABLED, true);
        customFieldData.put(Constants.ORGANIZATION_ID, "org1");

        CustomFieldEntity entity = new CustomFieldEntity();
        entity.setCustomFieldData(customFieldData);

        when(cbServerProperties.getCustomFieldStatusUpdateValidationFilePath()).thenReturn("validation.json");
        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("user1");
        when(customFieldRepository.findByCustomFiledIdAndIsActiveTrue("cf1")).thenReturn(Optional.of(entity));

        ApiResponse response = service.updateCustomFieldStatus(requestNode, "token");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testUpdateCustomFieldStatus_exceptionThrown() {
        ObjectNode requestNode = new ObjectMapper().createObjectNode();
        requestNode.put(Constants.CUSTOM_FIELD_ID, "cf1");
        requestNode.put(Constants.IS_ENABLED, true);

        when(cbServerProperties.getCustomFieldStatusUpdateValidationFilePath()).thenReturn("validation.json");
        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenThrow(new RuntimeException("Unexpected"));

        ApiResponse response = service.updateCustomFieldStatus(requestNode, "token");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void testUpdatePopupStatus_success() throws Exception {
        Map<String, Object> request = Map.of(
                Constants.ORGANIZATION_ID, "org1",
                Constants.IS_POPUP_ENABLED, true
        );

        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("user1");

        Map<String, Object> orgData = Map.of(
                Constants.CUSTOM_FIELDS_DATA, "{\"customFieldIds\":[\"cf1\"]}"
        );

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                any(), any(), any(), any(), any()))
                .thenReturn(List.of(orgData));

        Map<String, Object> cfData = new HashMap<>();
        cfData.put(Constants.CUSTOM_FIELD_IDS, List.of("cf1"));

        when(objectMapper.readValue(anyString(), eq(Map.class)))
                .thenReturn(cfData);

        when(objectMapper.writeValueAsString(any()))
                .thenReturn("{\"customFieldIds\":[\"cf1\"]}");

        ApiResponse response = service.updatePopupStatus(request, "token");

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
    }

    @Test
    void testUpdatePopupStatus_invalidToken() {
        Map<String, Object> request = Map.of(
                Constants.ORGANIZATION_ID, "org1",
                Constants.IS_POPUP_ENABLED, true
        );

        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("");

        ApiResponse response = service.updatePopupStatus(request, "token");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
    }

    @Test
    void testUpdatePopupStatus_validationFails() {
        Map<String, Object> request = new HashMap<>(); // empty map

        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("user1");

        ApiResponse response = service.updatePopupStatus(request, "token");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testUpdatePopupStatus_orgNotFound() {
        Map<String, Object> request = Map.of(
                Constants.ORGANIZATION_ID, "org1",
                Constants.IS_POPUP_ENABLED, true
        );

        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("user1");

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        ApiResponse response = service.updatePopupStatus(request, "token");

        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
    }

    @Test
    void testUpdatePopupStatus_emptyCustomFieldsData() {
        Map<String, Object> request = Map.of(
                Constants.ORGANIZATION_ID, "org1",
                Constants.IS_POPUP_ENABLED, true
        );

        Map<String, Object> orgData = Map.of(Constants.CUSTOM_FIELDS_DATA, "");

        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("user1");
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), any()))
                .thenReturn(List.of(orgData));

        ApiResponse response = service.updatePopupStatus(request, "token");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testUpdatePopupStatus_noCustomFieldIds() throws Exception {
        ObjectMapper realMapper = new ObjectMapper();

        Map<String, Object> request = Map.of(
                Constants.ORGANIZATION_ID, "org1",
                Constants.IS_POPUP_ENABLED, true
        );

        Map<String, Object> orgData = Map.of(
                Constants.CUSTOM_FIELDS_DATA,
                realMapper.writeValueAsString(new HashMap<>())
        );

        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("user1");
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), any()))
                .thenReturn(List.of(orgData));

        when(objectMapper.readValue(anyString(), eq(Map.class)))
                .thenReturn(new HashMap<>());

        ApiResponse response = service.updatePopupStatus(request, "token");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testUpdatePopupStatus_statusAlreadySet() throws Exception {
        ObjectMapper realMapper = new ObjectMapper();

        Map<String, Object> request = Map.of(
                Constants.ORGANIZATION_ID, "org1",
                Constants.IS_POPUP_ENABLED, true
        );

        Map<String, Object> cfData = new HashMap<>();
        cfData.put(Constants.CUSTOM_FIELD_IDS, List.of("cf1"));
        cfData.put(Constants.IS_POPUP_ENABLED, true);

        Map<String, Object> orgData = Map.of(
                Constants.CUSTOM_FIELDS_DATA,
                realMapper.writeValueAsString(cfData)
        );

        when(accessTokenValidator.fetchUserIdFromAccessToken("token")).thenReturn("user1");
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), any()))
                .thenReturn(List.of(orgData));

        when(objectMapper.readValue(anyString(), eq(Map.class)))
                .thenReturn(cfData);

        ApiResponse response = service.updatePopupStatus(request, "token");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testUpdatePopupStatus_exception() {
        Map<String, Object> request = Map.of(
                Constants.ORGANIZATION_ID, "org1",
                Constants.IS_POPUP_ENABLED, true
        );

        when(accessTokenValidator.fetchUserIdFromAccessToken("token"))
                .thenThrow(new RuntimeException("boom"));

        ApiResponse response = service.updatePopupStatus(request, "token");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    private byte[] generateValidExcelFileBytes() throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             Workbook workbookXSS = new XSSFWorkbook()) {

            Sheet sheetWorkbook = workbookXSS.createSheet("Sheet1");
            Row header = sheetWorkbook.createRow(0);
            header.createCell(0).setCellValue("header");

            Row row1 = sheetWorkbook.createRow(1);
            row1.createCell(0).setCellValue("data");

            workbookXSS.write(out);
            return out.toByteArray();
        }
    }


    private MultipartFile validExcelFile(int levels) throws Exception {
        return validExcelFile(levels, "name1");
    }

    private MultipartFile validExcelFile(int levels, String headerName) throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sheetWorkbook = wb.createSheet();
        Row headerRowNew = sheetWorkbook.createRow(0);
        for (int i = 0; i < levels; i++) {
            headerRowNew.createCell(i).setCellValue(headerName);
        }
        Row newDataRow = sheetWorkbook.createRow(1);
        for (int i = 0; i < levels; i++) {
            newDataRow.createCell(i).setCellValue("value" + i);
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        wb.write(bos);
        wb.close();
        return new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new ByteArrayInputStream(bos.toByteArray()));
    }

    public ObjectNode createSampleCustomFieldJson() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode customFieldJson = mapper.createObjectNode();
        customFieldJson.put(Constants.CUSTOM_FIELD_ID, "cf123");
        customFieldJson.put(Constants.ATTRIBUTE_NAME, "attr1");
        customFieldJson.put(Constants.ORGANISATION_ID, "org1");
        customFieldJson.put(Constants.NAME, "testName");
        customFieldJson.put(Constants.IS_MANDATORY, true);
        return customFieldJson;
    }
    public Map<String, Object> createSampleJsonMap() {
        Map<String, Object> jsonMap = new HashMap<>();

        List<Map<String, Object>> cfData = new ArrayList<>();
        Map<String, Object> field = new HashMap<>();
        field.put(Constants.ATTRIBUTE_NAME, "attr1");
        field.put(Constants.NAME, "name1");
        field.put(Constants.LEVEL, 1);
        cfData.add(field);

        jsonMap.put(Constants.ORGANIZATION_ID, "org1");
        jsonMap.put(Constants.CUSTOM_FIELD_DATA, cfData);

        return jsonMap;
    }


}
