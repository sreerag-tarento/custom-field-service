package com.igot.cb.customFields.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.util.ApiResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Service
public interface CustomFieldsService {

    ApiResponse createCustomFields(JsonNode customFieldsData, String token);

    ApiResponse readCustomField(String customFieldId, String token);

    ApiResponse updateCustomField(JsonNode customFieldData, String token);

    ApiResponse deleteCustomField(String customFieldId, String token);

    ApiResponse searchCustomFields(SearchCriteria searchCriteria,String orgId,String authToken,boolean isAdmin);

    ApiResponse uploadMasterListCustomField(MultipartFile multipartFile, String customFieldsMasterDataJson, String token);

    ApiResponse updateMasterListCustomField(MultipartFile file, String customFieldsMasterDataJson, String token);

    ApiResponse updateCustomFieldStatus(JsonNode updateCustomFieldStatusData, String token);

    ApiResponse updatePopupStatus(Map<String, Object> popupStatusData, String token);
}
