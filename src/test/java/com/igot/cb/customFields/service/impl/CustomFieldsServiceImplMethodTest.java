package com.igot.cb.customFields.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.igot.cb.pores.elasticsearch.dto.SearchResult;
import com.igot.cb.pores.elasticsearch.service.EsUtilService;
import com.igot.cb.pores.util.CbServerProperties;
import com.igot.cb.pores.util.Constants;
import com.igot.cb.transactional.cassandrautils.CassandraOperationImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomFieldsServiceImplMethodTest {

    @InjectMocks
    private CustomFieldsServiceImpl service;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private CassandraOperationImpl cassandraOperation;

    @Mock
    private CbServerProperties cbServerProperties;

    @Mock
    private EsUtilService esUtilService;


    @Test
    void test_removeCustomFieldFromOrg_avoidsNPE_whenDataIsValid() throws Exception {
        // Arrange
        String customFieldId = "field123";
        String orgId = "org456";

        String customFieldsJson = "{ \"customFieldIds\": [\"field123\", \"field456\"], \"customFieldsCount\": 2 }";

        Map<String, Object> orgMap = new HashMap<>();
        orgMap.put(Constants.CUSTOM_FIELDS_DATA, customFieldsJson);
        orgMap.put(Constants.ID, orgId);
        List<Map<String, Object>> orgList = Collections.singletonList(orgMap);

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.ORG_TABLE),
                anyMap(),
                anyList(),
                isNull()))
                .thenReturn(orgList);

        Map<String, Object> customFieldsDataMap = new HashMap<>();
        customFieldsDataMap.put(Constants.CUSTOM_FIELD_IDS, new ArrayList<>(Arrays.asList("field123", "field456")));
        customFieldsDataMap.put(Constants.CUSTOM_FIELDS_COUNT, 2);

        when(objectMapper.readValue(customFieldsJson, Map.class))
                .thenReturn(customFieldsDataMap);

        ObjectNode mockJsonNode = mock(ObjectNode.class);
        when(mockJsonNode.get(Constants.ORGANIZATION_ID)).thenReturn(new TextNode(orgId));
        when(mockJsonNode.has(Constants.TYPE)).thenReturn(false);

        // Act
        Method method = CustomFieldsServiceImpl.class.getDeclaredMethod("removeCustomFieldFromOrg", String.class, JsonNode.class);
        method.setAccessible(true);

        method.invoke(service, customFieldId, mockJsonNode);

        // Assert
        verify(cassandraOperation).updateRecord(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.ORG_TABLE), anyMap());
    }


    @Test
    void test_addCustomFieldToOrg_maxLimitExceeded() throws Exception {
        // Arrange
        String customFieldId = "cf1";
        String orgId = "org1";

        JsonNode customFieldData = mock(JsonNode.class);
        JsonNode orgIdNode = mock(JsonNode.class);

        when(customFieldData.get(Constants.ORGANIZATION_ID)).thenReturn(orgIdNode);
        when(orgIdNode.asText()).thenReturn(orgId);
        when(customFieldData.has(Constants.TYPE)).thenReturn(false);

        Map<String, Object> existingFields = new HashMap<>();
        existingFields.put(Constants.CUSTOM_FIELD_IDS, new ArrayList<>(List.of("cf2", "cf3")));
        existingFields.put(Constants.CUSTOM_FIELDS_COUNT, 10);

        String existingFieldsJson = "{ \"customFieldIds\": [\"cf2\", \"cf3\"], \"customFieldsCount\": 10 }";

        Map<String, Object> orgMap = new HashMap<>();
        orgMap.put(Constants.CUSTOM_FIELDS_DATA, existingFieldsJson);

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                anyString(), anyString(), anyMap(), anyList(), isNull()))
                .thenReturn(List.of(orgMap));

        when(objectMapper.readValue(existingFieldsJson, Map.class))
                .thenReturn(existingFields);

        when(cbServerProperties.getCustomFieldMaxAllowedCount()).thenReturn(10);

        // Act
        Method method = CustomFieldsServiceImpl.class.getDeclaredMethod("addCustomFieldToOrg", String.class, JsonNode.class);
        method.setAccessible(true);

        Object result = method.invoke(service, customFieldId, customFieldData);

        // Assert
        assertNotNull(result);
        assertTrue(result.toString().contains("Cannot enable this custom field"),
                "Should return message about exceeding the maximum limit");
    }

    private String invokeValidatePopupStatusData(Map<String, Object> data) throws Exception {
        Method method = CustomFieldsServiceImpl.class.getDeclaredMethod("validatePopupStatusData", Map.class);
        method.setAccessible(true);
        return (String) method.invoke(service, data);
    }

    @Test
    void testWhenPopupStatusDataIsNull() throws Exception {
        String result = invokeValidatePopupStatusData(null);
        assertEquals("Popup status data object is empty.", result);
    }

    @Test
    void testWhenPopupStatusDataIsEmpty() throws Exception {
        String result = invokeValidatePopupStatusData(Collections.emptyMap());
        assertEquals("Popup status data object is empty.", result);
    }

    @Test
    void testWhenOrganizationIdIsMissing() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put(Constants.IS_POPUP_ENABLED, true);

        String result = invokeValidatePopupStatusData(data);
        assertTrue(result.contains("Failed due to missing params"));
        assertTrue(result.contains(Constants.ORGANIZATION_ID));
        assertFalse(result.contains(Constants.IS_POPUP_ENABLED));
    }

    @Test
    void testWhenPopupEnabledIsMissing() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put(Constants.ORGANIZATION_ID, "org123");

        String result = invokeValidatePopupStatusData(data);
        assertTrue(result.contains("Failed due to missing params"));
        assertTrue(result.contains(Constants.IS_POPUP_ENABLED));
        assertFalse(result.contains(Constants.ORGANIZATION_ID));
    }

    @Test
    void testWhenBothFieldsAreMissing() throws Exception {
        Map<String, Object> data = new HashMap<>();
        String result = invokeValidatePopupStatusData(data);
        assertTrue(result.contains("Popup status data object is empty."));
    }

    @Test
    void testWhenOrganizationIdIsEmptyString() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put(Constants.ORGANIZATION_ID, "");  // Empty string
        data.put(Constants.IS_POPUP_ENABLED, true);

        String result = invokeValidatePopupStatusData(data);
        assertTrue(result.contains(Constants.ORGANIZATION_ID));
        assertFalse(result.contains(Constants.IS_POPUP_ENABLED));
    }

    @Test
    void testWhenBothFieldsArePresentAndValid() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put(Constants.ORGANIZATION_ID, "org123");
        data.put(Constants.IS_POPUP_ENABLED, true);

        String result = invokeValidatePopupStatusData(data);
        assertEquals("", result);  // no error
    }

    private String invokeValidate(List<String> attrList, String orgId, String excludeId) throws Exception {
        Method method = CustomFieldsServiceImpl.class
                .getDeclaredMethod("validateAttributeNameNotExistsInES", List.class, String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, attrList, orgId, excludeId);
    }

    @Test
    void testSearchResultIsNull() throws Exception {
        when(cbServerProperties.getCustomFieldEntity()).thenReturn("custom_field");
        when(cbServerProperties.getCustomFieldElasticMappingJsonPath()).thenReturn("/some/path");
        when(esUtilService.searchDocuments(anyString(), any(), anyString())).thenReturn(null);
        String result = invokeValidate(List.of("attr1"), "org1", null);
        assertNull(result);
    }

    @Test
    void testSearchResultDataIsNull() throws Exception {
        when(cbServerProperties.getCustomFieldEntity()).thenReturn("custom_field");
        when(cbServerProperties.getCustomFieldElasticMappingJsonPath()).thenReturn("/some/path");
        SearchResult mockResult = new SearchResult();
        mockResult.setData(null);
        when(esUtilService.searchDocuments(anyString(), any(), anyString())).thenReturn(mockResult);
        String result = invokeValidate(List.of("attr1"), "org1", null);
        assertNull(result);
    }

    @Test
    void testSearchResultDataIsEmpty() throws Exception {
        when(cbServerProperties.getCustomFieldEntity()).thenReturn("custom_field");
        when(cbServerProperties.getCustomFieldElasticMappingJsonPath()).thenReturn("/some/path");
        SearchResult mockResult = new SearchResult();
        mockResult.setData(Collections.emptyList());
        when(esUtilService.searchDocuments(anyString(), any(), anyString())).thenReturn(mockResult);
        String result = invokeValidate(List.of("attr1"), "org1", null);
        assertNull(result);
    }

    @Test
    void testDataObjectNotMap() throws Exception {
        when(cbServerProperties.getCustomFieldEntity()).thenReturn("custom_field");
        when(cbServerProperties.getCustomFieldElasticMappingJsonPath()).thenReturn("/some/path");
        SearchResult mockResult = new SearchResult();
        mockResult.setData(List.of()); // Not a map
        when(esUtilService.searchDocuments(anyString(), any(), anyString())).thenReturn(mockResult);
        String result = invokeValidate(List.of("attr1"), "org1", null);
        assertNull(result);
    }

    @Test
    void testOriginalDataNotList() throws Exception {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put(Constants.ORIGINAL_CUSTOM_FIELD_DATA, "not-a-list");

        SearchResult mockResult = new SearchResult();
        mockResult.setData(List.of(dataMap));
        when(cbServerProperties.getCustomFieldEntity()).thenReturn("custom_field");
        when(cbServerProperties.getCustomFieldElasticMappingJsonPath()).thenReturn("/some/path");
        when(esUtilService.searchDocuments(anyString(), any(), anyString())).thenReturn(mockResult);

        String result = invokeValidate(List.of("attr1"), "org1", null);
        assertNull(result);
    }

    @Test
    void testItemNotMap() throws Exception {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put(Constants.ORIGINAL_CUSTOM_FIELD_DATA, List.of("not-map"));

        SearchResult mockResult = new SearchResult();
        mockResult.setData(List.of(dataMap));

        when(cbServerProperties.getCustomFieldEntity()).thenReturn("custom_field");
        when(cbServerProperties.getCustomFieldElasticMappingJsonPath()).thenReturn("/some/path");
        when(esUtilService.searchDocuments(anyString(), any(), anyString())).thenReturn(mockResult);

        String result = invokeValidate(List.of("attr1"), "org1", null);
        assertNull(result);
    }

    @Test
    void testAttrNullOrNotInList() throws Exception {
        Map<String, Object> item = new HashMap<>();
        // No attributeName field

        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put(Constants.ORIGINAL_CUSTOM_FIELD_DATA, List.of(item));

        SearchResult mockResult = new SearchResult();
        mockResult.setData(List.of(dataMap));

        when(cbServerProperties.getCustomFieldEntity()).thenReturn("custom_field");
        when(cbServerProperties.getCustomFieldElasticMappingJsonPath()).thenReturn("/some/path");
        when(esUtilService.searchDocuments(anyString(), any(), anyString())).thenReturn(mockResult);

        String result = invokeValidate(List.of("attr1"), "org1", null);
        assertNull(result);
    }

    @Test
    void testExcludeCustomFieldIdSkipsMatchedData() throws Exception {
        Map<String, Object> item = new HashMap<>();
        item.put(Constants.ATTRIBUTE_NAME, "attr1");

        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put(Constants.CUSTOM_FIELD_ID, "exclude123"); // same as excludeCustomFieldId
        dataMap.put(Constants.ORIGINAL_CUSTOM_FIELD_DATA, List.of(item));

        SearchResult mockResult = new SearchResult();
        mockResult.setData(List.of(dataMap));

        when(cbServerProperties.getCustomFieldEntity()).thenReturn("custom_field");
        when(cbServerProperties.getCustomFieldElasticMappingJsonPath()).thenReturn("/some/path");
        when(esUtilService.searchDocuments(anyString(), any(), anyString())).thenReturn(mockResult);

        String result = invokeValidate(List.of("attr1"), "org1", "exclude123");
        assertNull(result); // should skip
    }

    @Test
    void testDuplicateAttributeFound() throws Exception {
        Map<String, Object> item = new HashMap<>();
        item.put(Constants.ATTRIBUTE_NAME, "attr1");

        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put(Constants.CUSTOM_FIELD_ID, "someId");
        dataMap.put(Constants.ORIGINAL_CUSTOM_FIELD_DATA, List.of(item));

        SearchResult mockResult = new SearchResult();
        mockResult.setData(List.of(dataMap));

        when(cbServerProperties.getCustomFieldEntity()).thenReturn("custom_field");
        when(cbServerProperties.getCustomFieldElasticMappingJsonPath()).thenReturn("/some/path");
        when(esUtilService.searchDocuments(anyString(), any(), anyString())).thenReturn(mockResult);

        String result = invokeValidate(List.of("attr1"), "org1", "anotherId");
        assertNotNull(result);
        assertTrue(result.contains("Custom field(s) with attributeName(s) 'attr1' already exist."));
    }

}
