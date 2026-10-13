/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */

package dev.dora.domain.model;

import java.util.List;

/**
 * Domain representation of a RAG response — pure Java, no framework annotations.
 *
 * @author Rodrigo Prestes Machado
 */
public class RagResponse {

    /** The original query text that was used to retrieve the context passages. */
    private final String query;
    /** Ordered list of context passages retrieved from the embedding store. */
    private final List<String> contexts;
    /** Highest similarity score among the retrieved passages. */
    private final double score;

    /**
     * Creates a RagResponse with the query, retrieved contexts and top score.
     *
     * @param query    original query text
     * @param contexts list of retrieved context passages, best match first
     * @param score    highest similarity score among the results
     */
    public RagResponse(String query, List<String> contexts, double score) {
        this.query = query;
        this.contexts = contexts;
        this.score = score;
    }

    public String getQuery() {
        return query;
    }

    public List<String> getContexts() {
        return contexts;
    }

    public double getScore() {
        return score;
    }

    /**
     * Returns the top-ranked context passage, or an empty string if no results were found.
     *
     * @return best-matching context passage, or {@code ""}
     */
    public String getFirstContext() {
        return contexts.isEmpty() ? "" : contexts.get(0);
    }
}
