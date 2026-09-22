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
 * Domain representation of a legal process ("processo") lookup result scraped
 * from a court website (e.g. TJRS).
 *
 * @author Rodrigo Prestes Machado
 */
public class ProcessLookupResult {

    /** Canonical CNJ process number that was looked up. */
    private final String processNumber;

    /** Cleaned Markdown describing the process status, parties and movement history. */
    private final String markdown;

    /** URL the result was scraped from, kept for traceability/debugging. */
    private final String sourceUrl;

    /**
     * Creates a ProcessLookupResult.
     *
     * @param processNumber canonical CNJ process number
     * @param markdown      cleaned Markdown content describing the process
     * @param sourceUrl     URL the result was scraped from
     */
    public ProcessLookupResult(String processNumber, String markdown, String sourceUrl) {
        this.processNumber = processNumber;
        this.markdown = markdown;
        this.sourceUrl = sourceUrl;
    }

    public String getProcessNumber() {
        return processNumber;
    }

    public String getMarkdown() {
        return markdown;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }
}
