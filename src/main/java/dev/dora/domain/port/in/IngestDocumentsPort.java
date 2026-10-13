/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */

package dev.dora.domain.port.in;

import java.util.List;

/**
 * Driving port (in) for ingesting documents from a local directory or scraped from URLs.
 *
 * @author Rodrigo Prestes Machado
 */
public interface IngestDocumentsPort {

    /**
     * Reads all supported documents from the given directory and ingests them into the embedding
     * store.
     *
     * @param documentsPath file-system path to the directory containing the source documents
     */
    void execute(String documentsPath);

    /**
     * Scrapes the given URLs and ingests the resulting documents into the embedding store.
     *
     * @param urls URLs to scrape and ingest
     */
    void executeUrls(List<String> urls);
}
