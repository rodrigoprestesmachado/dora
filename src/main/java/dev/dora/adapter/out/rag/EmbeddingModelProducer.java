/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */

package dev.dora.adapter.out.rag;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * Produces the {@link EmbeddingModel} CDI bean used for RAG vector-similarity search
 * and document ingestion.
 *
 * <p>Uses the local, in-process all-MiniLM-L6-v2 ONNX model (384 dimensions), which
 * requires no external API and matches the {@code quarkus.langchain4j.pgvector.dimension}
 * already configured for this project. Unlike {@code quarkus-langchain4j-openai}, the
 * {@code langchain4j-embeddings-all-minilm-l6-v2} dependency is a plain library, not a
 * Quarkus extension, so the bean must be produced explicitly.
 *
 * @author Rodrigo Prestes Machado
 */
@ApplicationScoped
public class EmbeddingModelProducer {

    /**
     * Produces the singleton embedding model instance.
     *
     * @return a local all-MiniLM-L6-v2 embedding model
     */
    @Produces
    @ApplicationScoped
    public EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }
}
