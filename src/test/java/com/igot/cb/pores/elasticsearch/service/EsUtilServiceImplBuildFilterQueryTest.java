package com.igot.cb.pores.elasticsearch.service;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.json.JsonData;
import com.igot.cb.pores.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class EsUtilServiceImplBuildFilterQueryTest {

    @InjectMocks
    private EsUtilServiceImpl esUtilService;

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @Mock
    private ObjectMapper objectMapper;

    private Method buildFilterQueryMethod;

    @BeforeEach
    void setUp() throws Exception {
        buildFilterQueryMethod = EsUtilServiceImpl.class.getDeclaredMethod("buildFilterQuery", Map.class);
        buildFilterQueryMethod.setAccessible(true);
    }

    @Test
    void testBuildFilterQuery_nullMap() throws Exception {
        BoolQuery.Builder result = (BoolQuery.Builder) buildFilterQueryMethod.invoke(esUtilService, (Map<String, Object>) null);
        assertNotNull(result);
        BoolQuery query = result.build();
        assertNotNull(query);
    }

    @Test
    void testBuildFilterQuery_emptyMap() throws Exception {
        Map<String, Object> filterMap = new HashMap<>();
        BoolQuery.Builder result = (BoolQuery.Builder) buildFilterQueryMethod.invoke(esUtilService, filterMap);
        assertNotNull(result);
    }

    @Test
    void testBuildFilterQuery_mustNotWithArrayList() throws Exception {
        Map<String, Object> filterMap = new HashMap<>();
        ArrayList<String> mustNotValues = new ArrayList<>();
        mustNotValues.add("value1");
        mustNotValues.add("value2");
        filterMap.put("must_not", mustNotValues);

        BoolQuery.Builder result = (BoolQuery.Builder) buildFilterQueryMethod.invoke(esUtilService, filterMap);
        assertNotNull(result);
    }

    @Test
    void testBuildFilterQuery_booleanValue() throws Exception {
        Map<String, Object> filterMap = new HashMap<>();
        filterMap.put("isActive", true);

        BoolQuery.Builder result = (BoolQuery.Builder) buildFilterQueryMethod.invoke(esUtilService, filterMap);
        assertNotNull(result);
    }

    @Test
    void testBuildFilterQuery_listValue() throws Exception {
        Map<String, Object> filterMap = new HashMap<>();
        List<String> values = Arrays.asList("value1", "value2");
        filterMap.put("status", values);

        BoolQuery.Builder result = (BoolQuery.Builder) buildFilterQueryMethod.invoke(esUtilService, filterMap);
        assertNotNull(result);
    }

    @Test
    void testBuildFilterQuery_stringValue() throws Exception {
        Map<String, Object> filterMap = new HashMap<>();
        filterMap.put("category", "test");

        BoolQuery.Builder result = (BoolQuery.Builder) buildFilterQueryMethod.invoke(esUtilService, filterMap);
        assertNotNull(result);
    }

    @Test
    void testBuildFilterQuery_setValue() throws Exception {
        Map<String, Object> filterMap = new HashMap<>();
        Set<String> values = new HashSet<>();
        values.add("value1");
        values.add("value2");
        filterMap.put("tags", values);

        BoolQuery.Builder result = (BoolQuery.Builder) buildFilterQueryMethod.invoke(esUtilService, filterMap);
        assertNotNull(result);
    }

    @Test
    void testBuildFilterQuery_rangeQuery_gte() throws Exception {
        Map<String, Object> filterMap = new HashMap<>();
        Map<String, Object> rangeMap = new HashMap<>();
        rangeMap.put(Constants.SEARCH_OPERATION_GREATER_THAN_EQUALS, JsonData.of(100));
        filterMap.put("price", rangeMap);

        BoolQuery.Builder result = (BoolQuery.Builder) buildFilterQueryMethod.invoke(esUtilService, filterMap);
        assertNotNull(result);
    }

    @Test
    void testBuildFilterQuery_rangeQuery_lte() throws Exception {
        Map<String, Object> filterMap = new HashMap<>();
        Map<String, Object> rangeMap = new HashMap<>();
        rangeMap.put(Constants.SEARCH_OPERATION_LESS_THAN_EQUALS, JsonData.of(500));
        filterMap.put("price", rangeMap);

        BoolQuery.Builder result = (BoolQuery.Builder) buildFilterQueryMethod.invoke(esUtilService, filterMap);
        assertNotNull(result);
    }

    @Test
    void testBuildFilterQuery_rangeQuery_gt() throws Exception {
        Map<String, Object> filterMap = new HashMap<>();
        Map<String, Object> rangeMap = new HashMap<>();
        rangeMap.put(Constants.SEARCH_OPERATION_GREATER_THAN, JsonData.of(50));
        filterMap.put("score", rangeMap);

        BoolQuery.Builder result = (BoolQuery.Builder) buildFilterQueryMethod.invoke(esUtilService, filterMap);
        assertNotNull(result);
    }

    @Test
    void testBuildFilterQuery_rangeQuery_lt() throws Exception {
        Map<String, Object> filterMap = new HashMap<>();
        Map<String, Object> rangeMap = new HashMap<>();
        rangeMap.put(Constants.SEARCH_OPERATION_LESS_THAN, JsonData.of(1000));
        filterMap.put("amount", rangeMap);

        BoolQuery.Builder result = (BoolQuery.Builder) buildFilterQueryMethod.invoke(esUtilService, filterMap);
        assertNotNull(result);
    }

    @Test
    void testBuildFilterQuery_nestedMap_boolean() throws Exception {
        Map<String, Object> filterMap = new HashMap<>();
        Map<String, Object> nestedMap = new HashMap<>();
        nestedMap.put("enabled", true);
        filterMap.put("settings", nestedMap);

        BoolQuery.Builder result = (BoolQuery.Builder) buildFilterQueryMethod.invoke(esUtilService, filterMap);
        assertNotNull(result);
    }

    @Test
    void testBuildFilterQuery_multipleConditions() throws Exception {
        Map<String, Object> filterMap = new HashMap<>();
        
        // Boolean condition
        filterMap.put("isActive", true);
        
        // String condition
        filterMap.put("status", "published");
        
        // List condition
        filterMap.put("categories", Arrays.asList("tech", "science"));
        
        // Range condition
        Map<String, Object> rangeMap = new HashMap<>();
        rangeMap.put(Constants.SEARCH_OPERATION_GREATER_THAN_EQUALS, JsonData.of(10));
        rangeMap.put(Constants.SEARCH_OPERATION_LESS_THAN_EQUALS, JsonData.of(100));
        filterMap.put("rating", rangeMap);

        BoolQuery.Builder result = (BoolQuery.Builder) buildFilterQueryMethod.invoke(esUtilService, filterMap);
        assertNotNull(result);
    }
}