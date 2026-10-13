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

import dev.dora.domain.model.DocumentData;
import java.util.Optional;

/**
 * Driven port (out) for web scraping operations.
 *
 * @author Rodrigo Prestes Machado
 */
public interface WebScraperPort {

    /**
     * Scrapes a URL and returns the document content.
     *
     * @param url the URL to scrape
     * @return Optional containing the document data, or empty if scraping failed
     */
    Optional<DocumentData> scrapeToDocument(String url);

    /**
     * Clears the markdown output directory if saving is enabled.
     */
    void clearMarkdownOutputDirIfEnabled();
}
