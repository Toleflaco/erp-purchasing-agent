package dev.toleflaco.erp_purchasing_agent.rag;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@SpringBootTest(properties = "spring.ai.mcp.client.enabled=false")
class DocumentIngestionTest {

    @Value("classpath:rag/erp-domain.md")
    Resource erpDomainDocument;

    @Autowired
    VectorStore vectorStore;

    @Autowired
    StringRedisTemplate redisTemplate;


    @BeforeEach
    void setup() {
        Set<String> keys = redisTemplate.keys("default:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }


    @Test
    void shouldSplitErpDomainDocumentIntoChunksWithExpectedMetadata() throws IOException {
        // Given
        String text = erpDomainDocument.getContentAsString(StandardCharsets.UTF_8);
        TokenTextSplitter splitter = TokenTextSplitter.builder().withChunkSize(200).build();

        // When
        List<Document> chunks = splitter.split(Document.builder().text(text).build());

        // Then
        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks.get(0).getText()).startsWith("# **Documento Técnico");
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.getMetadata()).containsKeys("chunk_index","total_chunks","parent_document_id"));
        for (int i = 0; i < chunks.size();i++) {
            assertThat(chunks.get(i).getMetadata().get("chunk_index")).isEqualTo(i);
        }
        assertThat(chunks).extracting(c -> c.getMetadata().get("total_chunks"))
                .containsOnly(chunks.size());
        assertThat(chunks).extracting(c -> c.getMetadata().get("parent_document_id"))
                .containsOnly(chunks.get(0).getMetadata().get("parent_document_id"));
    }

    @Test
    void shouldRetrieveRelevantChunkFromIngestedErpDomainDocument() throws IOException {
        // Given
        String text = erpDomainDocument.getContentAsString(StandardCharsets.UTF_8);
        TokenTextSplitter splitter = TokenTextSplitter.builder().withChunkSize(200).build();
        List<Document> chunks = splitter.split(Document.builder().text(text).build());
        vectorStore.add(chunks);

        // When
        List<Document> results = vectorStore.similaritySearch(SearchRequest.builder().query("procesos del ciclo procure to pay").topK(3).build());

        // Then
        assertThat(results).hasSizeBetween(1,3);
        assertThat(results).extracting(Document::getText)
                .anySatisfy(txt -> assertThat(txt).containsIgnoringCase("procure"));
    }

}
