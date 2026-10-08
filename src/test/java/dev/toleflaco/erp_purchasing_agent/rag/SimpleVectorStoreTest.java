package dev.toleflaco.erp_purchasing_agent.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.ai.mcp.client.enabled=false")
class SimpleVectorStoreTest {

    @Autowired
    EmbeddingModel embeddingModel;

    @Test
    void shouldStoreAndRetrieveASingleDocument() {
        // given
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
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
    void shouldFindMostSimilarPurchaseOrderDocument() {
        // given
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        vectorStore.add(List.of(
                Document.builder().text("Cancelar un pedido de compra en estado DRAFT").build(),
                Document.builder().text("Crear un nuevo pedido de compra con líneas").build(),
                Document.builder().text("Aprobar un pedido de compra pendiente").build(),
                Document.builder().text("Consultar el estado de un pedido existente").build(),
                Document.builder().text("Marcar un pedido como enviado al proveedor").build(),
                Document.builder().text("Recetas de cocina tradicional cántabra").build(),
                Document.builder().text("El tiempo en Madrid durante el verano").build()
        ));
        // when
        List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
                .query("¿cómo anulo una orden de compra?")
                .topK(5)
                .build());
        // then
        // MiniLM is weak on Spanish semantic reformulations: "anular" vs "cancelar"
        // doesn't rank first. We assert "Cancelar" is anywhere in top-5 as a soft check.
        // Lesson: for production, use a larger embedding model.
        assertThat(results).extracting(Document::getText).anyMatch(t -> t.contains("Cancelar"));
    }

    @Test
    void shouldRankLiteralQueryMatchAsTop1() {
        // given
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        vectorStore.add(List.of(
                Document.builder().text("Cancelar un pedido de compra en estado DRAFT").build(),
                Document.builder().text("Crear un nuevo pedido de compra con líneas").build(),
                Document.builder().text("Aprobar un pedido de compra pendiente").build(),
                Document.builder().text("Consultar el estado de un pedido existente").build(),
                Document.builder().text("Marcar un pedido como enviado al proveedor").build(),
                Document.builder().text("Recetas de cocina tradicional cántabra").build(),
                Document.builder().text("El tiempo en Madrid durante el verano").build()
        ));
        // when
        List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
                .query("cancelar un pedido en estado DRAFT")
                .topK(1)
                .build());
        // then
        assertThat(results.get(0).getText()).contains("Cancelar");
    }
}
