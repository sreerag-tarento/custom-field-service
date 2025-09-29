package com.igot.cb.customFields.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.customFields.entity.CustomFieldEntity;
import com.igot.cb.customFields.repository.CustomFieldRepository;
import com.igot.cb.customFields.service.CustomFieldsService;
import com.igot.cb.pores.cache.CacheService;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.elasticsearch.dto.SearchResult;
import com.igot.cb.pores.elasticsearch.service.EsUtilService;
import com.igot.cb.pores.util.*;
import com.igot.cb.transactional.cassandrautils.CassandraOperationImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class CustomFieldsServiceImpl implements CustomFieldsService {
    @Autowired
    private CustomFieldRepository customFieldRepository;
    @Autowired
    private PayloadValidation payloadValidation;
    @Autowired
    private AccessTokenValidator accessTokenValidator;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private EsUtilService esUtilService;
    @Autowired
    private CacheService cacheService;
    @Autowired
    private CbServerProperties cbServerProperties;
    @Autowired
    private CassandraOperationImpl cassandraOperation;

    @Override
    public ApiResponse createCustomFields(JsonNode customFieldsData, String token) {
        log.info("CustomFieldsServiceImpl::createCustomFields:creating custom fields");
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.CREATE_CUSTOM_FIELD_API);
        payloadValidation.validatePayload(cbServerProperties.getCustomFieldValidationFilePath(), customFieldsData);

        try {
            String userId = accessTokenValidator.fetchUserIdFromAccessToken(token);
            if (StringUtils.isBlank(userId)) {
                response.getParams().setStatus(Constants.FAILED);
                response.getParams().setErrMsg(Constants.INVALID_AUTH_TOKEN);
                response.setResponseCode(HttpStatus.UNAUTHORIZED);
                return response;
            }

            String errorMessage = validateAttributeNameNotExistsInES(Arrays.asList(customFieldsData.get(Constants.ATTRIBUTE_NAME).asText()), customFieldsData.get(Constants.ORGANIZATION_ID).asText(),null);
            if (StringUtils.isNotBlank(errorMessage)) {
                ProjectUtil.returnErrorMsg(errorMessage, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                return response;
            }

            Map<String, Object> originalFieldData = new HashMap<>();
            originalFieldData.put(Constants.NAME, customFieldsData.get(Constants.NAME).asText());
            originalFieldData.put(Constants.ATTRIBUTE_NAME, customFieldsData.get(Constants.ATTRIBUTE_NAME).asText());
            List<Map<String, Object>> originalCustomFieldData = new ArrayList<>();
            originalCustomFieldData.add(originalFieldData);

            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            String formattedCurrentTime = getFormattedCurrentTime(currentTime);
            ObjectNode customFieldsDataObjectNode = (ObjectNode) customFieldsData;
            customFieldsDataObjectNode.putPOJO(Constants.ORIGINAL_CUSTOM_FIELD_DATA, originalCustomFieldData);
            customFieldsDataObjectNode.put(Constants.CREATED_BY, userId);
            customFieldsDataObjectNode.put(Constants.CREATED_ON, formattedCurrentTime);
            customFieldsDataObjectNode.put(Constants.UPDATED_ON, formattedCurrentTime);
            customFieldsDataObjectNode.put(Constants.IS_ACTIVE, true);
            customFieldsDataObjectNode.put(Constants.IS_ENABLED, false);
            customFieldsDataObjectNode.put(Constants.IS_MANDATORY, customFieldsData.get(Constants.IS_MANDATORY).asBoolean(false));
            // Create and populate entity
            CustomFieldEntity customField = new CustomFieldEntity();
            String customFieldId = UUID.randomUUID().toString();
            customField.setCustomFiledId(customFieldId);
            customField.setCustomFieldData(customFieldsDataObjectNode);
            customField.setIsMandatory(customFieldsData.get(Constants.IS_MANDATORY).asBoolean(false));
            customField.setIsActive(true);
            customField.setCreatedOn(currentTime);
            customField.setUpdatedOn(currentTime);

            // Save to database
            customFieldRepository.save(customField);

            // Convert to map for response and ES
            Map<String, Object> customFieldMap = objectMapper.convertValue(customFieldsDataObjectNode, Map.class);
            customFieldMap.put(Constants.CUSTOM_FIELD_ID, customFieldId);

            esUtilService.addDocument(cbServerProperties.getCustomFieldEntity(), customFieldId, customFieldMap, cbServerProperties.getCustomFieldElasticMappingJsonPath());

            // Store in Redis cache
            JsonNode responseNode = objectMapper.valueToTree(customFieldMap);
            cacheService.putCache("CUSTOM_FIELD_" + customFieldId, responseNode);

            // Set success response
            response.setResponseCode(HttpStatus.OK);
            response.getParams().setStatus(Constants.SUCCESS);
            response.setMessage(Constants.SUCCESS);
            response.setResult(customFieldMap);
        } catch (Exception e) {
            log.error("Failed to create custom field: {}", e.getMessage(), e);
            ProjectUtil.returnErrorMsg(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, response, Constants.FAILED);
            return response;
        }
        return response;
    }

    @Override
    public ApiResponse readCustomField(String customFieldId, String token) {
        log.info("CustomFieldsServiceImpl::readCustomField: Getting custom field with ID: {}", customFieldId);
        ApiResponse response = new ApiResponse("customField.read");

        try {
            // Validate user token
            String userId = accessTokenValidator.fetchUserIdFromAccessToken(token);
            if (StringUtils.isBlank(userId)) {
                response.getParams().setStatus(Constants.FAILED);
                response.getParams().setErrMsg(Constants.INVALID_AUTH_TOKEN);
                response.setResponseCode(HttpStatus.UNAUTHORIZED);
                return response;
            }

            // Try to fetch from Redis cache first
            String cachedData = null;//cacheService.getCache("CUSTOM_FIELD_" + customFieldId);
            if (cachedData != null) {
                Map<String, Object> customFieldMap = objectMapper.convertValue(cachedData, Map.class);
                response.setResponseCode(HttpStatus.OK);
                response.setMessage(Constants.SUCCESS);
                response.setResult(customFieldMap);
                return response;
            }

            // If not in cache, fetch from database
            Optional<CustomFieldEntity> customFieldOpt = customFieldRepository.findByCustomFiledIdAndIsActiveTrue(customFieldId);
            if (customFieldOpt.isEmpty()) {
                response.getParams().setStatus(Constants.FAILED);
                response.getParams().setErrMsg("Custom field not found with ID: " + customFieldId);
                response.setResponseCode(HttpStatus.NOT_FOUND);
                return response;
            }

            CustomFieldEntity customField = customFieldOpt.get();
            JsonNode customFieldData = customField.getCustomFieldData();
            Map<String, Object> customFieldMap = objectMapper.convertValue(customFieldData, Map.class);
            customFieldMap.put(Constants.CUSTOM_FIELD_ID, customFieldId);

            // Cache result for future requests
            cacheService.putCache("CUSTOM_FIELD_" + customFieldId, customFieldMap);

            response.setResponseCode(HttpStatus.OK);
            response.setMessage(Constants.SUCCESS);
            response.setResult(customFieldMap);

        } catch (Exception e) {
            log.error("Failed to read custom field: {}", e.getMessage(), e);
            response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
            response.getParams().setErrMsg("FAILED_TO_READ_CUSTOM_FIELD");
            response.setMessage(Constants.FAILED);
        }

        return response;
    }

    @Override
    public ApiResponse updateCustomField(JsonNode customFieldsData, String token) {
        log.info("CustomFieldsServiceImpl::updateCustomField: Updating customField.");
        ApiResponse response = new ApiResponse("customField.update");

        try {
            // Validate payload
            payloadValidation.validatePayload(Constants.CUSTOM_FIELD_UPDATE_VALIDATION_FILE_PATH, customFieldsData);

            // Validate user token
            String userId = accessTokenValidator.fetchUserIdFromAccessToken(token);
            if (StringUtils.isBlank(userId)) {
                ProjectUtil.returnErrorMsg(Constants.INVALID_AUTH_TOKEN, HttpStatus.UNAUTHORIZED, response, Constants.FAILED);
                return response;
            }

            String customFieldId = customFieldsData.get(Constants.CUSTOM_FIELD_ID).asText();

            // Check if field exists and is active
            Optional<CustomFieldEntity> customFieldOpt = customFieldRepository.findByCustomFiledIdAndIsActiveTrue(customFieldId);
            if (customFieldOpt.isEmpty()) {
                ProjectUtil.returnErrorMsg("Custom field not found or is inactive with ID: " + customFieldId, HttpStatus.NOT_FOUND, response, Constants.FAILED);
                return response;
            }

            CustomFieldEntity customField = customFieldOpt.get();
            JsonNode originalData = customField.getCustomFieldData();

            String attributeName = customFieldsData.get(Constants.ATTRIBUTE_NAME).asText();
            String organizationId = customFieldsData.get(Constants.ORGANISATION_ID).asText();
            String errorMessage = validateAttributeNameNotExistsInES(
                    Arrays.asList(attributeName), organizationId,customFieldId
            );

            if (StringUtils.isNotBlank(errorMessage)) {
                ProjectUtil.returnErrorMsg(errorMessage, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                return response;
            }

            if (originalData.has(Constants.ORGANISATION_ID) && !originalData.get(Constants.ORGANISATION_ID).asText().equals(customFieldsData.get(Constants.ORGANISATION_ID).asText())) {
                ProjectUtil.returnErrorMsg("Organisation ID cannot be changed", HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                return response;
            }

            ObjectNode customFieldsDataObjectNode = (ObjectNode) customFieldsData;
            Map<String, Object> originalFieldData = new HashMap<>();
            originalFieldData.put(Constants.NAME, customFieldsData.get(Constants.NAME).asText());
            originalFieldData.put(Constants.ATTRIBUTE_NAME, customFieldsData.get(Constants.ATTRIBUTE_NAME).asText());
            List<Map<String, Object>> originalCustomFieldData = new ArrayList<>();
            originalCustomFieldData.add(originalFieldData);
            customFieldsDataObjectNode.putPOJO(Constants.ORIGINAL_CUSTOM_FIELD_DATA, originalCustomFieldData);

            // Update entity data
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            String formattedCurrentTime = getFormattedCurrentTime(currentTime);

            customFieldsDataObjectNode.put(Constants.ORGANISATION_ID, customFieldOpt.get().getCustomFieldData().get(Constants.ORGANISATION_ID).asText());
            customFieldsDataObjectNode.put(Constants.UPDATED_BY, userId);
            customFieldsDataObjectNode.put(Constants.UPDATED_ON, formattedCurrentTime);
            customFieldsDataObjectNode.put(Constants.IS_ENABLED, false);
            customFieldsDataObjectNode.put(Constants.IS_MANDATORY, customFieldsData.get(Constants.IS_MANDATORY).asBoolean(originalData.get(Constants.IS_MANDATORY).asBoolean()));
            customFieldsDataObjectNode.put(Constants.IS_ACTIVE, originalData.get(Constants.IS_ACTIVE).asBoolean(originalData.get(Constants.IS_ACTIVE).asBoolean()));

            // Keep original creation info
            if (originalData.has(Constants.CREATED_BY)) {
                customFieldsDataObjectNode.put(Constants.CREATED_BY, originalData.get(Constants.CREATED_BY).asText());
            }
            if (originalData.has(Constants.CREATED_ON)) {
                customFieldsDataObjectNode.put(Constants.CREATED_ON, originalData.get(Constants.CREATED_ON).asText());
            }

            customField.setCustomFieldData(customFieldsDataObjectNode);
            customField.setIsMandatory(customFieldsData.get(Constants.IS_MANDATORY).asBoolean(originalData.get(Constants.IS_MANDATORY).asBoolean()));
            customField.setUpdatedOn(currentTime);
            customFieldRepository.save(customField);

            // Update ES and cache
            Map<String, Object> customFieldMap = objectMapper.convertValue(customFieldsDataObjectNode, Map.class);
            customFieldMap.put(Constants.CUSTOM_FIELD_ID, customFieldId);

            esUtilService.updateDocument(
                    cbServerProperties.getCustomFieldEntity(),
                    customFieldId,
                    customFieldMap,
                    cbServerProperties.getCustomFieldElasticMappingJsonPath()
            );

            removeCustomFieldFromOrg(customFieldId, customFieldsDataObjectNode);

            // Update Redis cache
            JsonNode responseNode = objectMapper.valueToTree(customFieldMap);
            cacheService.putCache("CUSTOM_FIELD_" + customFieldId, responseNode);
            response.setResponseCode(HttpStatus.OK);
            response.setMessage(Constants.SUCCESS);
            response.setResult(customFieldMap);

        } catch (Exception e) {
            ProjectUtil.returnErrorMsg("Failed to update custom field: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, response, Constants.FAILED);
            log.error("Failed to update custom field: {}", e.getMessage(), e);
            return response;
        }
        return response;
    }

    @Override
    public ApiResponse deleteCustomField(String customFieldId, String token) {
        log.info("CustomFieldsServiceImpl::deleteCustomField: Soft deleting custom field with ID: {}", customFieldId);
        ApiResponse response = new ApiResponse("customField.delete");

        try {
            String userId = accessTokenValidator.fetchUserIdFromAccessToken(token);
            if (StringUtils.isBlank(userId)) {
                ProjectUtil.returnErrorMsg(Constants.INVALID_AUTH_TOKEN, HttpStatus.UNAUTHORIZED, response, Constants.FAILED);
                return response;
            }

            Optional<CustomFieldEntity> customFieldOpt = customFieldRepository.findByCustomFiledIdAndIsActiveTrue(customFieldId);
            if (customFieldOpt.isEmpty()) {
                ProjectUtil.returnErrorMsg("Custom field not found with ID: " + customFieldId, HttpStatus.NOT_FOUND, response, Constants.FAILED);
                return response;
            }

            CustomFieldEntity customField = customFieldOpt.get();
            JsonNode customFieldData = customField.getCustomFieldData();
            boolean isEnabled = customFieldData.has(Constants.IS_ENABLED) && customFieldData.get(Constants.IS_ENABLED).asBoolean();

            // If enabled, first disable it by removing from org
            if (isEnabled) {
                log.info("Custom field is enabled, removing from org table before deletion: {}", customFieldId);
                try {
                    removeCustomFieldFromOrg(customFieldId, customFieldData);
                } catch (Exception e) {
                    log.error("Failed to remove custom field from org: {}", e.getMessage(), e);
                    ProjectUtil.returnErrorMsg("Failed to disable custom field before deletion: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR, response, Constants.FAILED);
                    return response;
                }
                log.info("Successfully removed custom field from org table: {}", customFieldId);
            }

            // Proceed with soft delete
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            String formattedCurrentTime = getFormattedCurrentTime(currentTime);
            ObjectNode customFieldDataNode = (ObjectNode) customFieldData;
            customFieldDataNode.put(Constants.IS_ACTIVE, false);
            customFieldDataNode.put(Constants.IS_ENABLED, false);
            customFieldDataNode.put(Constants.UPDATED_BY, userId);
            customFieldDataNode.put(Constants.UPDATED_ON, formattedCurrentTime);

            customField.setCustomFieldData(customFieldDataNode);
            customField.setIsActive(false);
            customField.setUpdatedOn(currentTime);
            customFieldRepository.save(customField);
            log.info("Custom field soft deleted, customFieldId: {}", customFieldId);

            Map<String, Object> customFieldMap = objectMapper.convertValue(customFieldDataNode, Map.class);
            customFieldMap.put(Constants.CUSTOM_FIELD_ID, customFieldId);
            esUtilService.updateDocument(
                    cbServerProperties.getCustomFieldEntity(),
                    customFieldId,
                    customFieldMap,
                    cbServerProperties.getCustomFieldElasticMappingJsonPath()
            );

            cacheService.deleteCache("CUSTOM_FIELD_" + customFieldId);
            log.info("Cache and ES entries updated for deleted custom field: {}", customFieldId);

            response.setResponseCode(HttpStatus.OK);
            response.setMessage(Constants.SUCCESS);
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put(Constants.CUSTOM_FIELD_ID_PARAM, customFieldId);
            resultMap.put(Constants.STATUS, Constants.DELETED);
            response.setResult(resultMap);
        } catch (Exception e) {
            log.error("Failed to delete custom field: {}", e.getMessage(), e);
            ProjectUtil.returnErrorMsg("Failed to delete custom field: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, response, Constants.FAILED);
            return response;
        }
        return response;
    }

    private String getFormattedCurrentTime(Timestamp currentTime) {
        ZonedDateTime zonedDateTime = currentTime.toInstant().atZone(ZoneId.systemDefault());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(Constants.TIME_FORMAT);
        return zonedDateTime.format(formatter);
    }

    @Override
    public ApiResponse searchCustomFields(SearchCriteria searchCriteria) {
        log.info("CustomFieldsServiceImpl::searchCustomFields: Searching custom fields");
        ApiResponse response = new ApiResponse("customField.search");

        try {

            // Default to active records if not specified
            if (!searchCriteria.getFilterCriteriaMap().containsKey(Constants.IS_ACTIVE)) {
                searchCriteria.getFilterCriteriaMap().put(Constants.IS_ACTIVE, true);
            }

            // Execute search in Elasticsearch
            SearchResult searchResult = esUtilService.searchDocuments(
                    cbServerProperties.getCustomFieldEntity(),
                    searchCriteria,
                    cbServerProperties.getCustomFieldElasticMappingJsonPath()
            );

            response.setResponseCode(HttpStatus.OK);
            response.setMessage(Constants.SUCCESS);
            response.getResult().put(Constants.SEARCH_RESULTS, searchResult);
        } catch (Exception e) {
            log.error("Failed to search custom fields: {}", e.getMessage(), e);
            ProjectUtil.returnErrorMsg("Failed to search custom fields: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, response, Constants.FAILED);
            return response;
        }
        return response;
    }

    @Override
    public ApiResponse uploadMasterListCustomField(MultipartFile file, String customFieldsMasterDataJson, String token) {
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.CREATE_CUSTOM_FIELD_API);
        log.info("CustomFieldsServiceImpl::uploadMasterListCustomField: Uploading master list custom field");

        Map<String, Object> customFieldsData;
        JsonNode payloadNode;
        try {
            customFieldsData = objectMapper.readValue(customFieldsMasterDataJson, Map.class);
            payloadNode = objectMapper.valueToTree(customFieldsData);
        } catch (Exception e) {
            ProjectUtil.returnErrorMsg(Constants.INVALID_JSON_CUSTOM_FIELDS_MASTER_DATA + e.getMessage(), HttpStatus.BAD_REQUEST, response, Constants.FAILED);
            return response;
        }
        payloadValidation.validatePayload(cbServerProperties.getCustomFieldListValidationFilePath(), payloadNode);

        String userId = accessTokenValidator.fetchUserIdFromAccessToken(token);
        if (StringUtils.isBlank(userId)) {
            ProjectUtil.returnErrorMsg(Constants.INVALID_AUTH_TOKEN, HttpStatus.UNAUTHORIZED, response, Constants.FAILED);
            return response;
        }

        List<String> attributeNames = new ArrayList<>();
        Object customFieldDataObj = customFieldsData.get(Constants.CUSTOM_FIELD_DATA);
        if (customFieldDataObj instanceof List<?> customFieldDataList) {
            for (Object fieldMetaObj : customFieldDataList) {
                Map<?, ?> fieldMeta = (Map<?, ?>) fieldMetaObj;
                attributeNames.add(String.valueOf(fieldMeta.get(Constants.ATTRIBUTE_NAME)));
            }
        }
        String organizationId = String.valueOf(customFieldsData.get(Constants.ORGANIZATION_ID));

        String errorMessage = validateAttributeNameNotExistsInES(attributeNames, organizationId, null);
        if (StringUtils.isNotBlank(errorMessage)) {
            ProjectUtil.returnErrorMsg(errorMessage, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
            return response;
        }

        if (file == null || file.isEmpty()) {
            ProjectUtil.returnErrorMsg(Constants.UPLOADED_FILE_IS_EMPTY, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
            return response;
        }

        String fileName = file.getOriginalFilename();
        String contentType = file.getContentType();
        List<String> extensions = Arrays.asList(cbServerProperties.getAllowedExtensions().split(","));
        List<String> contentTypes = Arrays.asList(cbServerProperties.getAllowedContentTypes().split(","));

        if (fileName == null ||
                extensions.stream().noneMatch(fileName::endsWith) ||
                contentType == null ||
                contentTypes.stream().noneMatch(contentType::equals)) {
            ProjectUtil.returnErrorMsg(Constants.ONLY_EXCEL_FILES_ALLOWED, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
            return response;
        }

        List<?> originalCustomFieldData = (List<?>) customFieldDataObj;
        customFieldsData.put(Constants.LEVELS, originalCustomFieldData.size());
        if (!(customFieldDataObj instanceof List<?> customFieldDataList) || customFieldDataList.isEmpty()) {
            ProjectUtil.returnErrorMsg(Constants.CUSTOM_FIELD_DATA_NON_EMPTY, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
            return response;
        }

        int maxLevel = cbServerProperties.getCustomFieldMaxLevel();
        if (customFieldDataList.size() > maxLevel) {
            ProjectUtil.returnErrorMsg(String.format(Constants.CUSTOM_FIELD_DATA_LEVELS_EXCEED, maxLevel), HttpStatus.BAD_REQUEST, response, Constants.FAILED);
            return response;
        }

        String[] headers;
        int levels;
        List<Row> dataRows = new ArrayList<>();

        Map<String, String> attributeToFieldNameMap = new HashMap<>();
        Map<String, String> nameToAttributeMap = new HashMap<>();
        for (Object fieldMetaObj : customFieldDataList) {
            Map<?, ?> fieldMeta = (Map<?, ?>) fieldMetaObj;
            String name = String.valueOf(fieldMeta.get(Constants.NAME));
            String attributeName = String.valueOf(fieldMeta.get(Constants.ATTRIBUTE_NAME));
            nameToAttributeMap.put(name, attributeName);
            attributeToFieldNameMap.put(attributeName, name);
        }

        try (InputStream is = file.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                ProjectUtil.returnErrorMsg(Constants.EXCEL_HEADER_ROW_REQUIRED, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                return response;
            }

            int excelColumns = headerRow.getLastCellNum();
            int jsonLevels = customFieldDataList.size();

            if (excelColumns != jsonLevels) {
                ProjectUtil.returnErrorMsg(
                        String.format(Constants.EXCEL_COLUMN_COUNT_MISMATCH, excelColumns, jsonLevels),
                        HttpStatus.BAD_REQUEST, response, Constants.FAILED
                );
                return response;
            }
            if (excelColumns > maxLevel) {
                ProjectUtil.returnErrorMsg(String.format(Constants.EXCEL_MORE_THAN_MAX_LEVELS, maxLevel), HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                return response;
            }

            levels = jsonLevels;

            headers = new String[levels];
            for (int j = 0; j < levels; j++) {
                String excelHeader = headerRow.getCell(j).getStringCellValue().trim();
                String attributeName = nameToAttributeMap.get(excelHeader);
                if (attributeName == null) {
                    ProjectUtil.returnErrorMsg("Excel header '" + excelHeader + "' does not match any field name in the request.", HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                    return response;
                }
                headers[j] = attributeName;
            }
            for (int i = 0; i < levels; i++) {
                Map<?, ?> fieldMeta = (Map<?, ?>) customFieldDataList.get(i);
                String expectedAttribute = String.valueOf(fieldMeta.get(Constants.ATTRIBUTE_NAME));
                int expectedLevel = Integer.parseInt(String.valueOf(fieldMeta.get(Constants.LEVEL)));
                if (!headers[i].equalsIgnoreCase(expectedAttribute)) {
                    ProjectUtil.returnErrorMsg(String.format(Constants.HEADER_MISMATCH, headers[i], expectedAttribute, (i + 1)), HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                    return response;
                }
                if (expectedLevel != (i + 1)) {
                    ProjectUtil.returnErrorMsg(String.format(Constants.LEVEL_MISMATCH, (i + 1), expectedLevel, (i + 1)), HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                    return response;
                }
            }
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row != null) dataRows.add(row);
            }
        } catch (Exception e) {
            ProjectUtil.returnErrorMsg(String.format(Constants.ERROR_READING_EXCEL, e.getMessage()), HttpStatus.BAD_REQUEST, response, Constants.FAILED);
            return response;
        }

        // Use the new method to get both hierarchies
        Map<String, ArrayNode> hierarchies = parseHierarchyWithReversed(headers, dataRows, levels, attributeToFieldNameMap);

        Timestamp currentTime = new Timestamp(System.currentTimeMillis());
        String formattedCurrentTime = getFormattedCurrentTime(currentTime);
        customFieldsData.put(Constants.CREATED_BY, userId);
        customFieldsData.put(Constants.CREATED_ON, formattedCurrentTime);
        customFieldsData.put(Constants.UPDATED_ON, formattedCurrentTime);
        customFieldsData.put(Constants.CUSTOM_FIELD_DATA, hierarchies.get(Constants.CUSTOM_FIELD_DATA));
        customFieldsData.put(Constants.REVERSED_ORDER_CUSTOM_FIELD_DATA, hierarchies.get(Constants.REVERSED_ORDER_CUSTOM_FIELD_DATA));
        customFieldsData.put(Constants.IS_ACTIVE, true);
        customFieldsData.put(Constants.IS_ENABLED, false);
        customFieldsData.put(Constants.ORIGINAL_CUSTOM_FIELD_DATA, originalCustomFieldData);

        JsonNode jsonNode = objectMapper.valueToTree(customFieldsData);
        CustomFieldEntity customField = new CustomFieldEntity();
        String customFieldId = UUID.randomUUID().toString();
        customField.setCustomFiledId(customFieldId);
        customField.setCustomFieldData(jsonNode);
        customField.setIsActive(true);
        customField.setIsMandatory(Boolean.TRUE.equals(customFieldsData.get(Constants.IS_MANDATORY)));
        customField.setCreatedOn(currentTime);
        customField.setUpdatedOn(currentTime);

        customFieldRepository.save(customField);

        Map<String, Object> customFieldMap = objectMapper.convertValue(customFieldsData, Map.class);
        customFieldMap.put(Constants.CUSTOM_FIELD_ID, customFieldId);
        esUtilService.addDocument(cbServerProperties.getCustomFieldEntity(), customFieldId, customFieldMap, cbServerProperties.getCustomFieldElasticMappingJsonPath());
        cacheService.putCache(Constants.CUSTOM_FIELD + customFieldId, objectMapper.valueToTree(customFieldMap));

        response.setResponseCode(HttpStatus.OK);
        response.getParams().setStatus(Constants.SUCCESS);
        response.setMessage(Constants.SUCCESS);
        response.setResult(customFieldMap);
        return response;
    }

    private Map<String, ArrayNode> parseHierarchyWithReversed(String[] headers, List<Row> dataRows, int levels, Map<String, String> attributeToFieldNameMap) {
        ArrayNode root = objectMapper.createArrayNode();
        // List of maps for each level to track existing nodes
        List<Map<String, ObjectNode>> levelMaps = new ArrayList<>();
        for (int i = 0; i < levels; i++) {
            levelMaps.add(new HashMap<>());
        }

        for (Row row : dataRows) {
            ArrayNode currentArray = root;
            ObjectNode parentNode = null;
            String parentFieldName = null;
            String parentFieldValue = null;

            for (int j = 0; j < levels; j++) {
                Cell valueCell = row.getCell(j);
                DataFormatter dataFormatter = new DataFormatter();
                // Inside the inner loop, replace value extraction with:
                String value = (valueCell != null) ? dataFormatter.formatCellValue(valueCell).trim() : null;
                if (value == null || value.isEmpty()) break;

                String attributeName = headers[j];
                // Get the proper field name from the attribute name
                String fieldName = attributeToFieldNameMap.getOrDefault(attributeName, attributeName);

                String key = attributeName + ":" + value + (parentFieldName != null ? "|" + parentFieldName + ":" + parentFieldValue : "");

                Map<String, ObjectNode> currentLevelMap = levelMaps.get(j);
                ObjectNode foundNode = currentLevelMap.get(key);

                if (foundNode == null) {
                    ObjectNode newNode = objectMapper.createObjectNode();
                    newNode.put(Constants.FIELD_NAME, fieldName);
                    newNode.put(Constants.FIELD_VALUE, value);
                    newNode.put(Constants.FIELD_ATTRIBUTE, attributeName);
                    if (parentFieldName != null && parentFieldValue != null) {
                        newNode.put(Constants.PARENT_FIELD_NAME, parentFieldName);
                        newNode.put(Constants.PARENT_FIELD_VALUE, parentFieldValue);
                    }
                    ArrayNode childArray = objectMapper.createArrayNode();
                    newNode.set(Constants.FIELD_VALUES, childArray);
                    currentArray.add(newNode);
                    currentLevelMap.put(key, newNode);
                    foundNode = newNode;
                }
                parentFieldName = fieldName;
                parentFieldValue = value;
                parentNode = foundNode;
                currentArray = (ArrayNode) foundNode.get(Constants.FIELD_VALUES);
            }
        }

        // Helper for reversed hierarchy
        List<ObjectNode> reversedList = new ArrayList<>();
        buildReversedHierarchyList(root, new ArrayList<>(), reversedList);

        ArrayNode reversedOrderCustomFieldData = objectMapper.valueToTree(reversedList);

        Map<String, ArrayNode> result = new HashMap<>();
        result.put(Constants.CUSTOM_FIELD_DATA, root);
        result.put(Constants.REVERSED_ORDER_CUSTOM_FIELD_DATA, reversedOrderCustomFieldData);
        return result;
    }

    private void buildReversedHierarchyList(JsonNode node, List<ObjectNode> path, List<ObjectNode> reversedList) {
        // Handle array nodes - process each child separately
        if (node.isArray()) {
            for (JsonNode child : node) {
                buildReversedHierarchyList(child, new ArrayList<>(), reversedList);
            }
            return;
        }

        // Add current node to the path
        path.add((ObjectNode) node);

        // Get children if any
        ArrayNode children = (ArrayNode) node.get(Constants.FIELD_VALUES);

        if (children == null || children.isEmpty()) {
            // This is a leaf node - create the reversed hierarchy
            ObjectNode reversedNode = null;

            // Start from leaf (last in path) and work up to root
            for (int i = 0; i < path.size(); i++) {
                ObjectNode current = objectMapper.createObjectNode();
                final ObjectNode nodeAtI = path.get(i);

                // Copy all fields except fieldValues
                nodeAtI.fieldNames().forEachRemaining(field -> {
                    if (!Constants.FIELD_VALUES.equals(field)) {
                        current.set(field, nodeAtI.get(field));
                    }
                });

                if (reversedNode == null) {
                    // First node (leaf) gets empty fieldValues
                    current.set(Constants.FIELD_VALUES, objectMapper.createArrayNode());
                    reversedNode = current;
                } else {
                    // Parent nodes get the previous node as child
                    ArrayNode arr = objectMapper.createArrayNode();
                    arr.add(reversedNode);
                    current.set(Constants.FIELD_VALUES, arr);
                    reversedNode = current;
                }
            }

            // Add the reversed hierarchy to the result list
            reversedList.add(reversedNode);
        } else {
            // For non-leaf nodes, process each child
            for (JsonNode child : children) {
                // Use a copy of the path for each child branch
                buildReversedHierarchyList(child, new ArrayList<>(path), reversedList);
            }
        }
    }

    @Override
    public ApiResponse updateMasterListCustomField(MultipartFile file, String customFieldsMasterDataJson, String token) {
        ApiResponse response = ProjectUtil.createDefaultResponse("customFields.update.masterList");
        log.info("CustomFieldsServiceImpl::updateMasterListCustomField: Updating master list custom field");
        try {
            Map<String, Object> customFieldsData;
            JsonNode payloadNode;
            try {
                customFieldsData = objectMapper.readValue(customFieldsMasterDataJson, Map.class);
                payloadNode = objectMapper.valueToTree(customFieldsData);
            } catch (Exception e) {
                ProjectUtil.returnErrorMsg(Constants.INVALID_JSON_CUSTOM_FIELDS_MASTER_DATA + e.getMessage(), HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                return response;
            }

            // Validate payload - this will check for required customFieldId
            payloadValidation.validatePayload(cbServerProperties.getCustomFieldListUpdateValidationFilePath(), payloadNode);

            String userId = accessTokenValidator.fetchUserIdFromAccessToken(token);
            if (StringUtils.isBlank(userId)) {
                ProjectUtil.returnErrorMsg(Constants.INVALID_AUTH_TOKEN, HttpStatus.UNAUTHORIZED, response, Constants.FAILED);
                return response;
            }

            // Check if custom field exists
            Optional<CustomFieldEntity> customFieldOpt = customFieldRepository.findByCustomFiledIdAndIsActiveTrue(
                    payloadNode.get(Constants.CUSTOM_FIELD_ID).asText()
            );
            if (customFieldOpt.isEmpty()) {
                ProjectUtil.returnErrorMsg("Custom field not found with ID: " + payloadNode.get(Constants.CUSTOM_FIELD_ID).asText(), HttpStatus.NOT_FOUND, response, Constants.FAILED);
                return response;
            }

            List<String> attributeNames = new ArrayList<>();
            Object customFieldDataObj = customFieldsData.get(Constants.CUSTOM_FIELD_DATA);
            if (customFieldDataObj instanceof List<?> customFieldDataList) {
                for (Object fieldMetaObj : customFieldDataList) {
                    Map<?, ?> fieldMeta = (Map<?, ?>) fieldMetaObj;
                    attributeNames.add(String.valueOf(fieldMeta.get(Constants.ATTRIBUTE_NAME)));
                }
            }
            String organizationId = String.valueOf(customFieldsData.get(Constants.ORGANIZATION_ID));

            String errorMessage = validateAttributeNameNotExistsInES(attributeNames, organizationId, payloadNode.get(Constants.CUSTOM_FIELD_ID).asText());
            if (StringUtils.isNotBlank(errorMessage)) {
                ProjectUtil.returnErrorMsg(errorMessage, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                return response;
            }

            CustomFieldEntity existingCustomField = customFieldOpt.get();
            JsonNode existingData = existingCustomField.getCustomFieldData();
            boolean isEnabled = existingData.has(Constants.IS_ENABLED) &&
                    existingData.get(Constants.IS_ENABLED).asBoolean();

            if (file == null || file.isEmpty()) {
                ProjectUtil.returnErrorMsg(Constants.UPLOADED_FILE_IS_EMPTY, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                return response;
            }

            String fileName = file.getOriginalFilename();
            String contentType = file.getContentType();
            List<String> extensions = Arrays.asList(cbServerProperties.getAllowedExtensions().split(","));
            List<String> contentTypes = Arrays.asList(cbServerProperties.getAllowedContentTypes().split(","));

            if (fileName == null ||
                    extensions.stream().noneMatch(fileName::endsWith) ||
                    contentType == null ||
                    contentTypes.stream().noneMatch(contentType::equals)) {
                ProjectUtil.returnErrorMsg(Constants.ONLY_EXCEL_FILES_ALLOWED, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                return response;
            }

            if (!(customFieldDataObj instanceof List<?> customFieldDataList) || customFieldDataList.isEmpty()) {
                ProjectUtil.returnErrorMsg(Constants.CUSTOM_FIELD_DATA_NON_EMPTY, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                return response;
            }

            // originalCustomFieldData is used to preserve the original data for response
            List<?> originalCustomFieldData = (List<?>) customFieldDataObj;
            customFieldsData.put(Constants.LEVELS, originalCustomFieldData.size());

            int maxLevel = cbServerProperties.getCustomFieldMaxLevel();
            if (customFieldDataList.size() > maxLevel) {
                ProjectUtil.returnErrorMsg(String.format(Constants.CUSTOM_FIELD_DATA_LEVELS_EXCEED, maxLevel), HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                return response;
            }

            String[] headers;
            int levels;
            List<Row> dataRows = new ArrayList<>();

            Map<String, String> attributeToFieldNameMap = new HashMap<>();
            Map<String, String> nameToAttributeMap = new HashMap<>();
            for (Object fieldMetaObj : customFieldDataList) {
                Map<?, ?> fieldMeta = (Map<?, ?>) fieldMetaObj;
                String name = String.valueOf(fieldMeta.get(Constants.NAME));
                String attributeName = String.valueOf(fieldMeta.get(Constants.ATTRIBUTE_NAME));
                nameToAttributeMap.put(name, attributeName);
                attributeToFieldNameMap.put(attributeName, name);
            }

            try (InputStream is = file.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
                Sheet sheet = workbook.getSheetAt(0);
                Row headerRow = sheet.getRow(0);
                if (headerRow == null) {
                    ProjectUtil.returnErrorMsg(Constants.EXCEL_HEADER_ROW_REQUIRED, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                    return response;
                }

                int excelColumns = headerRow.getLastCellNum();
                int jsonLevels = customFieldDataList.size();

                if (excelColumns != jsonLevels) {
                    ProjectUtil.returnErrorMsg(
                            String.format(Constants.EXCEL_COLUMN_COUNT_MISMATCH, excelColumns, jsonLevels),
                            HttpStatus.BAD_REQUEST, response, Constants.FAILED
                    );
                    return response;
                }
                if (excelColumns > maxLevel) {
                    ProjectUtil.returnErrorMsg(String.format(Constants.EXCEL_MORE_THAN_MAX_LEVELS, maxLevel), HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                    return response;
                }

                levels = jsonLevels;

                headers = new String[levels];
                for (int j = 0; j < levels; j++) {
                    String excelHeader = headerRow.getCell(j).getStringCellValue().trim();
                    String attributeName = nameToAttributeMap.get(excelHeader);
                    if (attributeName == null) {
                        ProjectUtil.returnErrorMsg("Excel header '" + excelHeader + "' does not match any field name in the request.", HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                        return response;
                    }
                    headers[j] = attributeName;
                }

                for (int i = 0; i < levels; i++) {
                    Map<?, ?> fieldMeta = (Map<?, ?>) customFieldDataList.get(i);
                    String expectedAttribute = String.valueOf(fieldMeta.get(Constants.ATTRIBUTE_NAME));
                    int expectedLevel = Integer.parseInt(String.valueOf(fieldMeta.get(Constants.LEVEL)));
                    if (!headers[i].equalsIgnoreCase(expectedAttribute)) {
                        ProjectUtil.returnErrorMsg(String.format(Constants.HEADER_MISMATCH, headers[i], expectedAttribute, (i + 1)), HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                        return response;
                    }
                    if (expectedLevel != (i + 1)) {
                        ProjectUtil.returnErrorMsg(String.format(Constants.LEVEL_MISMATCH, (i + 1), expectedLevel, (i + 1)), HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                        return response;
                    }
                }

                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row != null) dataRows.add(row);
                }
            } catch (Exception e) {
                ProjectUtil.returnErrorMsg(String.format(Constants.ERROR_READING_EXCEL, e.getMessage()), HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                return response;
            }

            // Use the method to get both hierarchies
            Map<String, ArrayNode> hierarchies = parseHierarchyWithReversed(headers, dataRows, levels, attributeToFieldNameMap);

            if (isEnabled) {
                try {
                    removeCustomFieldFromOrg(existingCustomField.getCustomFiledId(), existingData);
                    log.info("Removed custom field from org as part of master list update: {}", existingCustomField.getCustomFiledId());
                } catch (Exception e) {
                    log.error("Failed to remove custom field from org during update: {}", e.getMessage(), e);
                    ProjectUtil.returnErrorMsg("Failed to remove custom field from organization: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR, response, Constants.FAILED);
                    return response;
                }
            }

            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            String formattedCurrentTime = getFormattedCurrentTime(currentTime);

            // Preserve original creation info and other fields
            ObjectNode existingDataNode = (ObjectNode) existingData;
            if (existingDataNode.has(Constants.CREATED_BY)) {
                customFieldsData.put(Constants.CREATED_BY, existingDataNode.get(Constants.CREATED_BY).asText());
            }
            if (existingDataNode.has(Constants.CREATED_ON)) {
                customFieldsData.put(Constants.CREATED_ON, existingDataNode.get(Constants.CREATED_ON).asText());
            }

            customFieldsData.put(Constants.UPDATED_BY, userId);
            customFieldsData.put(Constants.UPDATED_ON, formattedCurrentTime);
            customFieldsData.put(Constants.CUSTOM_FIELD_DATA, hierarchies.get(Constants.CUSTOM_FIELD_DATA));
            customFieldsData.put(Constants.REVERSED_ORDER_CUSTOM_FIELD_DATA, hierarchies.get(Constants.REVERSED_ORDER_CUSTOM_FIELD_DATA));
            customFieldsData.put(Constants.ORIGINAL_CUSTOM_FIELD_DATA, originalCustomFieldData);
            customFieldsData.put(Constants.IS_ENABLED, false);
            customFieldsData.put(Constants.IS_ACTIVE, true);

            JsonNode jsonNode = objectMapper.valueToTree(customFieldsData);
            existingCustomField.setCustomFieldData(jsonNode);
            existingCustomField.setIsMandatory(Boolean.TRUE.equals(customFieldsData.get(Constants.IS_MANDATORY)));
            existingCustomField.setUpdatedOn(currentTime);

            customFieldRepository.save(existingCustomField);

            Map<String, Object> customFieldMap = objectMapper.convertValue(customFieldsData, Map.class);
            customFieldMap.put(Constants.CUSTOM_FIELD_ID, payloadNode.get(Constants.CUSTOM_FIELD_ID).asText());

            // Update ES document
            esUtilService.updateDocument(
                    cbServerProperties.getCustomFieldEntity(),
                    payloadNode.get(Constants.CUSTOM_FIELD_ID).asText(),
                    customFieldMap,
                    cbServerProperties.getCustomFieldElasticMappingJsonPath()
            );

            // Update Redis cache
            cacheService.putCache(Constants.CUSTOM_FIELD + payloadNode.get(Constants.CUSTOM_FIELD_ID).asText(), objectMapper.valueToTree(customFieldMap));

            response.setResponseCode(HttpStatus.OK);
            response.getParams().setStatus(Constants.SUCCESS);
            response.setMessage(Constants.SUCCESS);
            response.setResult(customFieldMap);
            return response;
        } catch (Exception e) {
            log.error("Exception in updateMasterListCustomField: {}", e.getMessage(), e);
            ProjectUtil.returnErrorMsg("Failed to update master list custom field: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, response, Constants.FAILED);
            return response;
        }
    }

    @Override
    public ApiResponse updateCustomFieldStatus(JsonNode updateCustomFieldStatusData, String token) {
        ApiResponse response = ProjectUtil.createDefaultResponse("customField.updateStatus");
        payloadValidation.validatePayload(cbServerProperties.getCustomFieldStatusUpdateValidationFilePath(), updateCustomFieldStatusData);
        try {
            String userId = accessTokenValidator.fetchUserIdFromAccessToken(token);
            if (StringUtils.isBlank(userId)) {
                ProjectUtil.returnErrorMsg(Constants.INVALID_AUTH_TOKEN, HttpStatus.UNAUTHORIZED, response, Constants.FAILED);
                return response;
            }

            String customFieldId = updateCustomFieldStatusData.get(Constants.CUSTOM_FIELD_ID).asText();
            boolean isEnabled = updateCustomFieldStatusData.get(Constants.IS_ENABLED).asBoolean();
            if (StringUtils.isBlank(customFieldId)) {
                ProjectUtil.returnErrorMsg("CustomFieldId is required", HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                return response;
            }

            Optional<CustomFieldEntity> customFieldOpt = customFieldRepository.findByCustomFiledIdAndIsActiveTrue(customFieldId);
            if (customFieldOpt.isEmpty()) {
                ProjectUtil.returnErrorMsg("Custom field not found with ID: " + customFieldId, HttpStatus.NOT_FOUND, response, Constants.FAILED);
                return response;
            }

            CustomFieldEntity customField = customFieldOpt.get();
            JsonNode customFieldData = customField.getCustomFieldData();
            boolean currentStatus = customFieldData.has(Constants.IS_ENABLED) &&
                    customFieldData.get(Constants.IS_ENABLED).asBoolean();

            if (isEnabled == currentStatus) {
                ProjectUtil.returnErrorMsg("isEnabled is already " + isEnabled + " for this custom field", HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                return response;
            }

            if (isEnabled) {
                String error = addCustomFieldToOrg(customFieldId, customFieldData);
                if (error != null) {
                    ProjectUtil.returnErrorMsg(error, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                    return response;
                }
            } else {
                removeCustomFieldFromOrg(customFieldId, customFieldData);
            }

            ObjectNode customFieldDataNode = (ObjectNode) customFieldData;
            customFieldDataNode.put(Constants.IS_ENABLED, isEnabled);
            customFieldDataNode.put(Constants.UPDATED_BY, userId);

            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            String formattedCurrentTime = getFormattedCurrentTime(currentTime);
            customFieldDataNode.put(Constants.UPDATED_ON, formattedCurrentTime);

            customField.setCustomFieldData(customFieldDataNode);
            customField.setUpdatedOn(currentTime);
            customFieldRepository.save(customField);

            Map<String, Object> customFieldMap = objectMapper.convertValue(customFieldDataNode, Map.class);
            customFieldMap.put(Constants.CUSTOM_FIELD_ID, customFieldId);

            esUtilService.updateDocument(
                    cbServerProperties.getCustomFieldEntity(),
                    customFieldId,
                    customFieldMap,
                    cbServerProperties.getCustomFieldElasticMappingJsonPath()
            );

            cacheService.putCache(Constants.CUSTOM_FIELD + customFieldId, objectMapper.valueToTree(customFieldMap));

            response.setResponseCode(HttpStatus.OK);
            response.getParams().setStatus(Constants.SUCCESS);
            response.setMessage(Constants.SUCCESS);
            return response;
        } catch (Exception e) {
            log.error("Failed to update custom field status: {}", e.getMessage(), e);
            ProjectUtil.returnErrorMsg("Failed to update custom field status: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, response, Constants.FAILED);
            return response;
        }
    }

    private String addCustomFieldToOrg(String customFieldId, JsonNode customFieldData) throws Exception {
        Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put(Constants.ID, customFieldData.get(Constants.ORGANIZATION_ID).asText());
        List<Map<String, Object>> orgList = cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                Constants.KEYSPACE_SUNBIRD, Constants.ORG_TABLE, propertyMap, Arrays.asList(Constants.CUSTOM_FIELDS_DATA), null);

        int fieldCount = 1;
        if (customFieldData.has(Constants.TYPE) && Constants.MASTER_LIST.equals(customFieldData.get(Constants.TYPE).asText())) {
            fieldCount = customFieldData.get(Constants.LEVELS).asInt();
        }

        Map<String, Object> customFieldsData;
        if (CollectionUtils.isEmpty(orgList) || StringUtils.isBlank((String) orgList.get(0).get(Constants.CUSTOM_FIELDS_DATA))) {
            customFieldsData = new HashMap<>();
            List<String> customFieldIds = new ArrayList<>();
            customFieldIds.add(customFieldId);
            customFieldsData.put(Constants.CUSTOM_FIELD_IDS, customFieldIds);
            customFieldsData.put(Constants.CUSTOM_FIELDS_COUNT, fieldCount);
        } else {
            customFieldsData = objectMapper.readValue((String) orgList.get(0).get(Constants.CUSTOM_FIELDS_DATA), Map.class);
            if (!customFieldsData.containsKey(Constants.CUSTOM_FIELD_IDS)) {
                customFieldsData.put(Constants.CUSTOM_FIELD_IDS, new ArrayList<String>());
            }
            if (!customFieldsData.containsKey(Constants.CUSTOM_FIELDS_COUNT)) {
                customFieldsData.put(Constants.CUSTOM_FIELDS_COUNT, 0);
            }
            int currentCount = ((Number) customFieldsData.get(Constants.CUSTOM_FIELDS_COUNT)).intValue();
            if (currentCount + fieldCount > cbServerProperties.getCustomFieldMaxAllowedCount()) {
                return "Cannot enable this custom field. The maximum limit of " + cbServerProperties.getCustomFieldMaxAllowedCount() + " enabled custom fields would be exceeded.";
            }
            List<String> customFieldIds = (List<String>) customFieldsData.get(Constants.CUSTOM_FIELD_IDS);
            if (CollectionUtils.isEmpty(customFieldIds) || !customFieldIds.contains(customFieldId)) {
                customFieldIds.add(customFieldId);
                customFieldsData.put(Constants.CUSTOM_FIELD_IDS, customFieldIds);
                customFieldsData.put(Constants.CUSTOM_FIELDS_COUNT, currentCount + fieldCount);
            }
        }
        Map<String, Object> orgUpdateData = new HashMap<>();
        orgUpdateData.put(Constants.ID, customFieldData.get(Constants.ORGANIZATION_ID).asText());
        orgUpdateData.put(Constants.CUSTOM_FIELDS_DATA, objectMapper.writeValueAsString(customFieldsData));
        cassandraOperation.updateRecord(Constants.KEYSPACE_SUNBIRD, Constants.ORG_TABLE, orgUpdateData);
        return null;
    }

    // Helper to remove custom field from org table
    private void removeCustomFieldFromOrg(String customFieldId, JsonNode customFieldData) throws Exception {
        Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put(Constants.ID, customFieldData.get(Constants.ORGANIZATION_ID).asText());
        List<Map<String, Object>> orgList = cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                Constants.KEYSPACE_SUNBIRD, Constants.ORG_TABLE, propertyMap, Arrays.asList(Constants.CUSTOM_FIELDS_DATA), null);

        if (!CollectionUtils.isEmpty(orgList) && !StringUtils.isBlank((String) orgList.get(0).get(Constants.CUSTOM_FIELDS_DATA))) {
            Map<String, Object> customFieldsData = objectMapper.readValue((String) orgList.get(0).get(Constants.CUSTOM_FIELDS_DATA), Map.class);
            if (customFieldsData.containsKey(Constants.CUSTOM_FIELD_IDS) && customFieldsData.containsKey(Constants.CUSTOM_FIELDS_COUNT)) {
                List<String> customFieldIds = (List<String>) customFieldsData.get(Constants.CUSTOM_FIELD_IDS);
                if (!CollectionUtils.isEmpty(customFieldIds) && customFieldIds.contains(customFieldId)) {
                    customFieldIds.remove(customFieldId);
                    customFieldsData.put(Constants.CUSTOM_FIELD_IDS, customFieldIds);

                    int fieldCount = 1;
                    if (customFieldData.has(Constants.TYPE) && Constants.MASTER_LIST.equals(customFieldData.get(Constants.TYPE).asText())) {
                        fieldCount = customFieldData.get(Constants.LEVELS).asInt();
                    }
                    int currentCount = ((Number) customFieldsData.get(Constants.CUSTOM_FIELDS_COUNT)).intValue();
                    customFieldsData.put(Constants.CUSTOM_FIELDS_COUNT, Math.max(0, currentCount - fieldCount));

                    Map<String, Object> orgUpdateData = new HashMap<>();
                    orgUpdateData.put(Constants.ID, customFieldData.get(Constants.ORGANIZATION_ID).asText());
                    orgUpdateData.put(Constants.CUSTOM_FIELDS_DATA, objectMapper.writeValueAsString(customFieldsData));
                    cassandraOperation.updateRecord(Constants.KEYSPACE_SUNBIRD, Constants.ORG_TABLE, orgUpdateData);
                }
            }
        }
    }

    @Override
    public ApiResponse updatePopupStatus(Map<String, Object> popupStatusData, String token) {
        ApiResponse response = ProjectUtil.createDefaultResponse("customField.updatePopupStatus");

        try {
            String userId = accessTokenValidator.fetchUserIdFromAccessToken(token);
            if (StringUtils.isBlank(userId)) {
                ProjectUtil.returnErrorMsg(Constants.INVALID_AUTH_TOKEN, HttpStatus.UNAUTHORIZED, response, Constants.FAILED);
                return response;
            }

            String error = validatePopupStatusData(popupStatusData);
            if (StringUtils.isNotBlank(error)) {
                ProjectUtil.returnErrorMsg(error, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                return response;
            }

            String organizationId = (String) popupStatusData.get(Constants.ORGANIZATION_ID);
            boolean isEnabled = Boolean.TRUE.equals(popupStatusData.get(Constants.IS_POPUP_ENABLED));

            Map<String, Object> propertyMap = new HashMap<>();
            propertyMap.put(Constants.ID, organizationId);
            List<Map<String, Object>> orgList = cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                    Constants.KEYSPACE_SUNBIRD, Constants.ORG_TABLE, propertyMap, Arrays.asList(Constants.CUSTOM_FIELDS_DATA), null);

            if (orgList.isEmpty()) {
                ProjectUtil.returnErrorMsg("Organization not found with ID: " + organizationId, HttpStatus.NOT_FOUND, response, Constants.FAILED);
                return response;
            }

            Map<String, Object> customFieldsData;

            if (StringUtils.isBlank((String) orgList.get(0).get(Constants.CUSTOM_FIELDS_DATA))) {
                customFieldsData = new HashMap<>();
            } else {
                customFieldsData = objectMapper.readValue((String) orgList.get(0).get(Constants.CUSTOM_FIELDS_DATA), Map.class);
            }

            if (MapUtils.isEmpty(customFieldsData)) {
                ProjectUtil.returnErrorMsg("No Active Custom Fields found for Organization ID: " + organizationId, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                return response;
            }

            // Validation for empty customFieldIds
            List<String> customFieldIds = (List<String>) customFieldsData.get(Constants.CUSTOM_FIELD_IDS);
            if (CollectionUtils.isEmpty(customFieldIds)) {
                ProjectUtil.returnErrorMsg("No custom fields are enabled for Organization ID: " + organizationId, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                return response;
            }

            if (customFieldsData.containsKey(Constants.IS_POPUP_ENABLED)) {
                boolean currentStatus = Boolean.TRUE.equals(customFieldsData.get(Constants.IS_POPUP_ENABLED));
                if (currentStatus == isEnabled) {
                    String status = isEnabled ? Constants.ENABLED : Constants.DISABLED;
                    ProjectUtil.returnErrorMsg("Popup is already " + status + " for this organization",
                            HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                    return response;
                }
            }

            customFieldsData.put(Constants.IS_POPUP_ENABLED, isEnabled);

            Map<String, Object> orgUpdateData = new HashMap<>();
            orgUpdateData.put(Constants.ID, organizationId);
            orgUpdateData.put(Constants.CUSTOM_FIELDS_DATA, objectMapper.writeValueAsString(customFieldsData));
            cassandraOperation.updateRecord(Constants.KEYSPACE_SUNBIRD, Constants.ORG_TABLE, orgUpdateData);

            Map<String, Object> result = new HashMap<>();
            result.put(Constants.ORGANISATION_ID, organizationId);
            result.put(Constants.POPUP_STATUS, isEnabled ? Constants.ENABLED : Constants.DISABLED);

            response.setResponseCode(HttpStatus.OK);
            response.getParams().setStatus(Constants.SUCCESS);
            response.setMessage(Constants.SUCCESS);
            response.setResult(result);
        } catch (Exception e) {
            log.error("Failed to update popup status: {}", e.getMessage(), e);
            ProjectUtil.returnErrorMsg("Failed to update popup status: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR, response, Constants.FAILED);
        }
        return response;
    }

    private String validatePopupStatusData(Map<String, Object> popupStatusData) {
        StringBuffer str = new StringBuffer();
        List<String> errList = new ArrayList<>();

        if (MapUtils.isEmpty(popupStatusData)) {
            str.append("Popup status data object is empty.");
            return str.toString();
        }

        if (StringUtils.isEmpty((String) popupStatusData.get(Constants.ORGANIZATION_ID))) {
            errList.add(Constants.ORGANIZATION_ID);
        }

        if (!popupStatusData.containsKey(Constants.IS_POPUP_ENABLED)) {
            errList.add(Constants.IS_POPUP_ENABLED);
        }

        if (!errList.isEmpty()) {
            str.append("Failed due to missing params - ").append(errList).append(".");
        }
        return str.toString();
    }

    private String validateAttributeNameNotExistsInES(
            List<String> attributeNameList,
            String organizationId,
            String excludeCustomFieldId
    ) {
        SearchCriteria searchCriteria = buildSearchCriteria(attributeNameList, organizationId, excludeCustomFieldId);

        SearchResult searchResult = esUtilService.searchDocuments(
                cbServerProperties.getCustomFieldEntity(),
                searchCriteria,
                cbServerProperties.getCustomFieldElasticMappingJsonPath()
        );

        if (searchResult == null || searchResult.getData() == null || searchResult.getData().isEmpty()) {
            return null;
        }

        Set<String> duplicateNames = extractDuplicateNames(searchResult, attributeNameList, excludeCustomFieldId);

        return duplicateNames.isEmpty()
                ? null
                : "Custom field(s) with attributeName(s) '" + String.join(", ", duplicateNames) + "' already exist.";
    }

    private SearchCriteria buildSearchCriteria(List<String> attributeNameList, String organizationId, String excludeCustomFieldId) {
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setFilterCriteriaMap(new HashMap<>());
        searchCriteria.setRequestedFields(new ArrayList<>());
        searchCriteria.getRequestedFields().add(Constants.CUSTOM_FIELD_FILTER_ATTRIBUTE);
        searchCriteria.getFilterCriteriaMap().put(Constants.ORGANIZATION_ID, organizationId);
        searchCriteria.getFilterCriteriaMap().put(Constants.CUSTOM_FIELD_FILTER_ATTRIBUTE, attributeNameList);
        if (StringUtils.isNotBlank(excludeCustomFieldId)) {
            searchCriteria.getRequestedFields().add(Constants.CUSTOM_FIELD_ID);
        }
        return searchCriteria;
    }

    private Set<String> extractDuplicateNames(SearchResult searchResult, List<String> attributeNameList, String excludeCustomFieldId) {
        Set<String> duplicateNames = new HashSet<>();

        for (Object dataObj : searchResult.getData()) {
            if (dataObj instanceof Map<?, ?> dataMap && !isSameCustomFieldId(dataMap, excludeCustomFieldId)) {
                Object originalData = dataMap.get(Constants.ORIGINAL_CUSTOM_FIELD_DATA);
                if (originalData instanceof List) {
                    findDuplicatesInOriginalData((List<?>) originalData, attributeNameList, duplicateNames);
                }
            }
        }
        return duplicateNames;
    }

    private boolean isSameCustomFieldId(Map<?, ?> dataMap, String excludeCustomFieldId) {
        Object idObj = dataMap.get(Constants.CUSTOM_FIELD_ID);
        return excludeCustomFieldId != null && excludeCustomFieldId.equals(String.valueOf(idObj));
    }

    private void findDuplicatesInOriginalData(List<?> originalData, List<String> attributeNameList, Set<String> duplicateNames) {
        for (Object item : originalData) {
            if (!(item instanceof Map<?, ?> itemMap)) {
                continue;
            }
            Object attr = itemMap.get(Constants.ATTRIBUTE_NAME);
            if (attr != null && attributeNameList.contains(attr.toString())) {
                duplicateNames.add(attr.toString());
            }
        }
    }

}
