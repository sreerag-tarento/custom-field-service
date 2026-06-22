package com.igot.cb.customFields.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.igot.cb.customFields.service.CustomFieldsService;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.util.ApiResponse;
import com.igot.cb.pores.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/customFields/v1")
public class CustomFieldsController {
    @Autowired
    private CustomFieldsService customFieldsService;

    @PostMapping("/create")
    public ResponseEntity<ApiResponse> createCustomFields(@RequestBody JsonNode customFieldsData,
                                                          @RequestHeader(Constants.X_AUTH_TOKEN) String token) {
        ApiResponse response = customFieldsService.createCustomFields(customFieldsData, token);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @GetMapping("/read/{customFieldId}")
    public ResponseEntity<ApiResponse> readCustomField(
            @PathVariable String customFieldId,
            @RequestHeader(Constants.X_AUTH_TOKEN) String token) {
        ApiResponse response = customFieldsService.readCustomField(customFieldId, token);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @PutMapping("/update")
    public ResponseEntity<ApiResponse> updateCustomField(
            @RequestBody JsonNode customFieldData,
            @RequestHeader(Constants.X_AUTH_TOKEN) String token) {
        ApiResponse response = customFieldsService.updateCustomField(customFieldData, token);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @DeleteMapping("/delete/{customFieldId}")
    public ResponseEntity<ApiResponse> deleteCustomField(
            @PathVariable String customFieldId,
            @RequestHeader(Constants.X_AUTH_TOKEN) String token) {
        ApiResponse response = customFieldsService.deleteCustomField(customFieldId, token);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @PostMapping("/search")
    public ResponseEntity<ApiResponse> searchAdminCustomFields(
            @RequestBody SearchCriteria searchCriteria, @RequestHeader(Constants.X_AUTH_TOKEN) String authToken,
            @RequestHeader(Constants.X_AUTH_USER_ORG_ID)String userOrgId) {
        ApiResponse response = customFieldsService.searchCustomFields(searchCriteria,userOrgId,authToken,true);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @PostMapping("/masterList/create")
    public ResponseEntity<ApiResponse> uploadCustomFieldHierarchy(
            @RequestParam("file") MultipartFile multipartFile,
            @RequestParam("metadata") String customFieldsMasterDataJson,
            @RequestHeader(Constants.X_AUTH_TOKEN) String token) {
        ApiResponse response = customFieldsService.uploadMasterListCustomField(multipartFile, customFieldsMasterDataJson, token);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @PostMapping(value = "/masterList/update")
    public ResponseEntity<ApiResponse> updateMasterListCustomField(
            @RequestParam("file") MultipartFile file,
            @RequestParam("metadata") String customFieldsMasterDataJson,
            @RequestHeader(Constants.X_AUTH_TOKEN) String token) {
        ApiResponse response = customFieldsService.updateMasterListCustomField(file, customFieldsMasterDataJson, token);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @PostMapping("/status/update")
    public ResponseEntity<ApiResponse> updateCustomFieldStatus(
            @RequestBody JsonNode updateCustomFieldStatusData,
            @RequestHeader(Constants.X_AUTH_TOKEN) String token) {
        ApiResponse response = customFieldsService.updateCustomFieldStatus(updateCustomFieldStatusData, token);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @PostMapping("/popup/update")
    public ResponseEntity<ApiResponse> updatePopupStatus(
            @RequestBody Map<String, Object> popupStatusData,
            @RequestHeader(Constants.X_AUTH_TOKEN) String token) {
        ApiResponse response = customFieldsService.updatePopupStatus(popupStatusData, token);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @PostMapping("/user/search")
    public ResponseEntity<ApiResponse> searchCustomFields(
            @RequestBody SearchCriteria searchCriteria, @RequestHeader(Constants.X_AUTH_TOKEN) String authToken,
            @RequestHeader(Constants.X_AUTH_USER_ORG_ID)String userOrgId) {
        ApiResponse response = customFieldsService.searchCustomFields(searchCriteria,userOrgId,authToken,false);
        return new ResponseEntity<>(response, response.getResponseCode());
    }
}
