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

import dev.dora.domain.model.RagQuery;
import dev.dora.domain.model.RagResponse;
import dev.dora.domain.port.out.EmbeddingRepository;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Implementation of the {@link EmbeddingRepository} port using LangChain4j, backed by the
 * pgvector-based {@link EmbeddingStore} and a local embedding model.
 *
 * @author Rodrigo Prestes Machado
 */
@ApplicationScoped
public class RagRetriever implements EmbeddingRepository {

    /** LangChain4j store that holds vectorised text segments. */
    private final EmbeddingStore<TextSegment> embeddingStore;
    /** Model used to vectorise queries before similarity search. */
    private final EmbeddingModel embeddingModel;

    /**
     * Creates a RagRetriever with all required collaborators.
     *
     * @param embeddingStore LangChain4j store holding vectorised text segments
     * @param embeddingModel model used to embed queries before similarity search
     */
    @Inject
    public RagRetriever(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RagResponse searchChunks(RagQuery query) {
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed(query.getQuery()).content())
                .minScore(query.getMinScore())
                .maxResults(query.getMaxResults())
                .build();

        var matches = embeddingStore.search(searchRequest).matches();
        var contexts = matches.stream()
                .map(match -> match.embedded().text())
                .toList();

        double score = matches.isEmpty() ? 0.0 : matches.get(0).score();

        return new RagResponse(query.getQuery(), contexts, score);
    }
}
