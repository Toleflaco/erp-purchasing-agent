package dev.toleflaco.erp_purchasing_agent.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.ai.mcp.client.enabled=false")
class RagExplorationTest {

    @Autowired
    EmbeddingModel embeddingModel;

    @Test
    void shouldEmbedASingleSentenceIntoAFixedSizeVector() {

        // given
        String sentence = "El agente ReAct cancela pedidos de compra en estado DRAFT o SENT";
        // when
        float[] vector = embeddingModel.embed(sentence);

        // then
        assertThat(vector).isNotNull();
        assertThat(vector).isNotEmpty();
        System.out.println("dimensions = " + vector.length);
        assertThat(vector).hasSize(embeddingModel.dimensions());

    }

    @Test
    void shouldPlaceSemanticallySimilarSentencesCloserThanUnrelatedOnes() {
        // given tres frases
        // experimento: mismo par de frases pero en ingles
        String aEn = "The ReAct agent cancels purchase orders in DRAFT or SENT state.";
        String bEn = "There is a tool to void purchase orders that have not been received.";

        float[] vaEn = embeddingModel.embed(aEn);
        float[] vbEn = embeddingModel.embed(bEn);
        double similarityAbEn = cosineSimilarity(vaEn, vbEn);


        String a = "El agente ReAct cancela pedidos de compra en estado DRAFT o SENT.";
        String b = "Hay una herramienta para anular ordenes de compra que no se han recibido.";
        String c = "La receta de paella necesita arroz, azafran y caldo de pescado.";
        // when calcular embeddings de las tres
        float[] vectorA = embeddingModel.embed(a);
        float[] vectorB = embeddingModel.embed(b);
        float[] vectorC = embeddingModel.embed(c);

        double similarityAB = cosineSimilarity(vectorA,vectorB);
        double similarityAC = cosineSimilarity(vectorA,vectorC);

        // then cosine(A,B) > cosine(A,C)
        assertThat(similarityAB).isGreaterThan(similarityAC);
    }

    private static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
