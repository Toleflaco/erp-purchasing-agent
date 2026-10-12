package dev.toleflaco.erp_purchasing_agent.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.ai.mcp.client.enabled=false")
class RedisVectorStoreTest {

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

    @Test
    void shouldRankLiteralQueryMatchAsTop1() {
        // given — mismo corpus de 7 Documents del dominio ERP
        vectorStore.add(List.of(
                Document.builder().text("Cancelar un pedido de compra en estado DRAFT").build(),
                Document.builder().text("Crear un nuevo pedido de compra con líneas").build(),
                Document.builder().text("Aprobar un pedido de compra pendiente").build(),
                Document.builder().text("Consultar el estado de un pedido existente").build(),
                Document.builder().text("Marcar un pedido como enviado al proveedor").build(),
                Document.builder().text("Recetas de cocina tradicional cántabra").build(),
                Document.builder().text("El tiempo en Madrid durante el verano").build()
        ));
        // when  — similaritySearch(query="cancelar un pedido en estado DRAFT", topK=1)
        List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
                .query("cancelar un pedido en estado DRAFT")
                .topK(1)
                .build());
        // then  — results.get(0).getText() contiene "Cancelar"
        assertThat(results.get(0).getText()).containsIgnoringCase("cancelar");
    }
}
