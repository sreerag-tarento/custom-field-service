package com.igot.cb.pores.elasticsearch.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import co.elastic.clients.elasticsearch.indices.GetIndexRequest;
import co.elastic.clients.elasticsearch.indices.GetIndexResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.elasticsearch.dto.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EsUtilServiceImplMethodTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ElasticsearchClient elasticsearchClient;

    @Mock
    private GetIndexResponse getIndexResponse;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private EsUtilServiceImpl esUtilService;

    private SearchCriteria searchCriteria;

    @BeforeEach
    void setUp() {
        searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);
        searchCriteria.setRequestedFields(List.of("field1", "field2"));
        Map<String, Object> filterCriteriaMap = new HashMap<>();
        searchCriteria.setFilterCriteriaMap((HashMap<String, Object>) filterCriteriaMap);
    }

    @Test
    void testSearchDocuments_success() throws IOException {
        String index = "test_index";
        String jsonFilePath = "schema.json";

        // Mock hit
        Hit<Object> hit1 = new Hit.Builder<>().id("doc1").index("index").source(Map.of("field", "value")).build();
        List<Hit<Object>> hitList = Arrays.asList(hit1);

        // Mock total hits
        TotalHits totalHits = new TotalHits.Builder().value(1L).relation(TotalHitsRelation.Eq).build();

        // Mock hits metadata
        HitsMetadata<Object> mockHitsMetadata = Mockito.mock(HitsMetadata.class);
        when(mockHitsMetadata.total()).thenReturn(totalHits);
        when(mockHitsMetadata.hits()).thenReturn(hitList);

        // Mock response
        SearchResponse<Object> mockResponse = Mockito.mock(SearchResponse.class);
        when(mockResponse.hits()).thenReturn(mockHitsMetadata);

        // Mock client
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class))).thenReturn(mockResponse);

        // Build searchCriteria
        searchCriteria.setRequestedFields(List.of("field"));
        searchCriteria.setFilterCriteriaMap(new HashMap<>());

        SearchResult result = esUtilService.searchDocuments(index, searchCriteria, jsonFilePath);

        assertNotNull(result);
        assertEquals(1, result.getTotalCount());
        assertFalse(result.getData().isEmpty());
    }

    @Test
    void testAddRequestedFieldsToSearchSourceBuilder_emptyFields() throws Exception {
        SearchRequest.Builder builder = new SearchRequest.Builder();
        searchCriteria.setRequestedFields(new ArrayList<>());

        Method method = EsUtilServiceImpl.class.getDeclaredMethod("addRequestedFieldsToSearchSourceBuilder", SearchCriteria.class, SearchRequest.Builder.class);
        method.setAccessible(true);
        method.invoke(esUtilService, searchCriteria, builder);
    }

    @Test
    void testAddFacetsToSearchSourceBuilder() throws Exception {
        SearchRequest.Builder builder = new SearchRequest.Builder();
        List<String> facets = List.of("communityId");

        Method method = EsUtilServiceImpl.class.getDeclaredMethod("addFacetsToSearchSourceBuilder", List.class, SearchRequest.Builder.class);
        method.setAccessible(true);
        assertDoesNotThrow(()-> method.invoke(esUtilService, facets, builder));
    }

    @Test
    void testIsIndexPresent_IndexExists() throws Exception {
        when(elasticsearchClient.indices().get(any(GetIndexRequest.class)))
                .thenReturn(getIndexResponse);

        boolean result = esUtilService.isIndexPresent("test-index");

        assertTrue(result);
        verify(elasticsearchClient.indices()).get(any(GetIndexRequest.class));
    }

    @Test
    void testIsIndexPresent_IndexThrowsIOException() throws Exception {
        when(elasticsearchClient.indices().get(any(GetIndexRequest.class)))
                .thenThrow(new IOException("Simulated failure"));

        boolean result = esUtilService.isIndexPresent("test-index");

        assertFalse(result);
        verify(elasticsearchClient.indices()).get(any(GetIndexRequest.class));
    }
}