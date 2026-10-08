package dev.toleflaco.erp_purchasing_agent.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.ai.mcp.client.enabled=false")
public class RedisVectorStoreTest {

    @Autowired
    VectorStore vectorStore;

    @Test
    void shouldStoreAndRetrieveASingleDocument() {
        // given
        vectorStore.add(List.of(Document.builder().text("Hola mundo desde el agente ReAct").build()));

        // when
        List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
                .query("hola mundo")
                .topK(1)
                .build());

        // then
        assertThat(results).hasSize(1);

    }
}
