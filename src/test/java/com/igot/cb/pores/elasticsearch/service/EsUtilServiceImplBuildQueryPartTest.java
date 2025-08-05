package com.igot.cb.pores.elasticsearch.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQueryField;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.pores.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class EsUtilServiceImplBuildQueryPartTest {

    @InjectMocks
    private EsUtilServiceImpl esUtilService;

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @Mock
    private ObjectMapper objectMapper;

    private Method buildQueryPartMethod;

    @BeforeEach
    void setUp() throws Exception {
        buildQueryPartMethod = EsUtilServiceImpl.class.getDeclaredMethod("buildQueryPart", Map.class);
        buildQueryPartMethod.setAccessible(true);
    }

    @Test
    void testBuildQueryPart_nullMap() throws Exception {
        Query result = (Query) buildQueryPartMethod.invoke(esUtilService, (Map<String, Object>) null);
        assertNotNull(result);
    }

    @Test
    void testBuildQueryPart_emptyMap() throws Exception {
        Map<String, Object> queryMap = new HashMap<>();
        Query result = (Query) buildQueryPartMethod.invoke(esUtilService, queryMap);
        assertNotNull(result);
    }

    @Test
    void testBuildQueryPart_boolQuery() throws Exception {
        Map<String, Object> queryMap = new HashMap<>();
        Map<String, Object> boolMap = new HashMap<>();
        List<Map<String, Object>> mustList = new ArrayList<>();
        Map<String, Object> termQuery = new HashMap<>();
        termQuery.put("field1", FieldValue.of("value1"));
        Map<String, Object> termWrapper = new HashMap<>();
        termWrapper.put(Constants.TERM, termQuery);
        mustList.add(termWrapper);
        boolMap.put(Constants.MUST, mustList);
        queryMap.put(Constants.BOOL, boolMap);

        Query result = (Query) buildQueryPartMethod.invoke(esUtilService, queryMap);
        assertNotNull(result);
    }

    @Test
    void testBuildQueryPart_termQuery() throws Exception {
        Map<String, Object> queryMap = new HashMap<>();
        Map<String, Object> termMap = new HashMap<>();
        termMap.put("status", FieldValue.of("active"));
        queryMap.put(Constants.TERM, termMap);

        Query result = (Query) buildQueryPartMethod.invoke(esUtilService, queryMap);
        assertNotNull(result);
    }

    @Test
    void testBuildQueryPart_termsQuery() throws Exception {
        Map<String, Object> queryMap = new HashMap<>();
        Map<String, Object> termsMap = new HashMap<>();
        List<FieldValue> values = Arrays.asList(FieldValue.of("val1"), FieldValue.of("val2"));
        termsMap.put("category", TermsQueryField.of(t -> t.value(values)));
        queryMap.put(Constants.TERMS, termsMap);

        Query result = (Query) buildQueryPartMethod.invoke(esUtilService, queryMap);
        assertNotNull(result);
    }

    @Test
    void testBuildQueryPart_matchQuery() throws Exception {
        Map<String, Object> queryMap = new HashMap<>();
        Map<String, Object> matchMap = new HashMap<>();
        matchMap.put("title", FieldValue.of("search text"));
        queryMap.put(Constants.MATCH, matchMap);

        Query result = (Query) buildQueryPartMethod.invoke(esUtilService, queryMap);
        assertNotNull(result);
    }

    static Stream<Arguments> rangeQueryProvider() {
        return Stream.of(
                Arguments.of("gt", "age", 10),
                Arguments.of("gte", "age", 18),
                Arguments.of("lt", "score", 100),
                Arguments.of("lte", "age", 65)
        );
    }

    @ParameterizedTest
    @MethodSource("rangeQueryProvider")
    void testBuildQueryPart_rangeQuery(String operator, String field, Object value) throws Exception {
        Map<String, Object> queryMap = new HashMap<>();
        Map<String, Object> rangeMap = new HashMap<>();
        Map<String, Object> conditions = new HashMap<>();
        conditions.put(operator, value);
        rangeMap.put(field, conditions);
        queryMap.put(Constants.RANGE, rangeMap);

        Query result = (Query) buildQueryPartMethod.invoke(esUtilService, queryMap);
        assertNotNull(result);
    }

    @Test
    void testBuildQueryPart_rangeQuery_unsupportedCondition() {
        Map<String, Object> queryMap = new HashMap<>();
        Map<String, Object> rangeMap = new HashMap<>();
        Map<String, Object> conditions = new HashMap<>();
        conditions.put("invalid", 50);
        rangeMap.put("field", conditions);
        queryMap.put(Constants.RANGE, rangeMap);

        assertThrows(Exception.class, () -> {
            buildQueryPartMethod.invoke(esUtilService, queryMap);
        });
    }

    @Test
    void testBuildQueryPart_mustNotWithList() throws Exception {
        Map<String, Object> queryMap = new HashMap<>();
        List<Map<String, Object>> mustNotList = new ArrayList<>();
        Map<String, Object> termQuery = new HashMap<>();
        termQuery.put("field1", FieldValue.of("value1"));
        Map<String, Object> termWrapper = new HashMap<>();
        termWrapper.put(Constants.TERM, termQuery);
        mustNotList.add(termWrapper);
        queryMap.put(Constants.MUST_NOT, mustNotList);

        Query result = (Query) buildQueryPartMethod.invoke(esUtilService, queryMap);
        assertNotNull(result);
    }

    @Test
    void testBuildQueryPart_mustNotWithNonList() {
        Map<String, Object> queryMap = new HashMap<>();
        queryMap.put(Constants.MUST_NOT, "not a list");

        assertThrows(Exception.class, () -> {
            buildQueryPartMethod.invoke(esUtilService, queryMap);
        });
    }

    @Test
    void testBuildQueryPart_unsupportedQueryType() {
        Map<String, Object> queryMap = new HashMap<>();
        queryMap.put("unsupported_query", "value");

        assertThrows(Exception.class, () -> {
            buildQueryPartMethod.invoke(esUtilService, queryMap);
        });
    }
}