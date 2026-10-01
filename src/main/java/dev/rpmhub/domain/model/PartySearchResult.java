/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */
package dev.rpmhub.domain.model;

/**
 * Domain representation of a TJRS search by exact person name.
 *
 * <p>{@link #markdown} holds the scraped table (people or processes). Instructional
 * text for the agent is added by the application service.
 *
 * @author Rodrigo Prestes Machado
 */
public class PartySearchResult {

    /** What the court page contained. */
    private final PartySearchKind kind;

    /** Markdown table of people or processes. Empty when nothing was listed. */
    private final String markdown;

    /** URL the result was scraped from, kept for traceability. */
    private final String sourceUrl;

    /**
     * Creates a PartySearchResult.
     *
     * @param kind       outcome of the search
     * @param markdown   scraped table, or an empty string when there is nothing to list
     * @param sourceUrl  URL the result was scraped from
     */
    public PartySearchResult(PartySearchKind kind, String markdown, String sourceUrl) {
        this.kind = kind;
        this.markdown = markdown == null ? "" : markdown;
        this.sourceUrl = sourceUrl;
    }

    public PartySearchKind getKind() {
        return kind;
    }

    public String getMarkdown() {
        return markdown;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }
}
