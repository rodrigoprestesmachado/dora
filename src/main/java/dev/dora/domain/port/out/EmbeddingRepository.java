/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */

package dev.dora.domain.port.out;

import dev.dora.domain.model.RagQuery;
import dev.dora.domain.model.RagResponse;

/**
 * Driven port (out) for vector-similarity search.
 *
 * @author Rodrigo Prestes Machado
 */
public interface EmbeddingRepository {

    /**
     * Searches for relevant chunks based on the provided RAG query.
     *
     * @param query the RAG query containing the search parameters
     * @return the RAG response with the retrieved context passages
     */
    RagResponse searchChunks(RagQuery query);
}
