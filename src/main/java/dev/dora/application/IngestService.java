/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */

package dev.dora.application;

import dev.dora.domain.model.DocumentData;
import dev.dora.domain.port.in.IngestDocumentsPort;
import dev.dora.domain.port.out.IngestPort;
import dev.dora.domain.port.out.WebScraperPort;
import io.quarkus.logging.Log;
import java.util.ArrayList;
import java.util.List;

/**
 * Application service for ingesting local documents and scraped URLs into the embedding
 * repository.
 *
 * <p>Plain Java — instantiated by the Composition Root
 * ({@code dev.dora.adapter.config.ApplicationBeans}).
 *
 * @author Rodrigo Prestes Machado
 */
public class IngestService implements IngestDocumentsPort {

    /** Repository used to store embedded document chunks in the vector store. */
    private final IngestPort ingestPort;

    /** Port used to fetch and convert URLs to Markdown documents before ingestion. */
    private final WebScraperPort webScraperPort;

    /**
     * Creates an IngestService with the given ingestion and scraping ports.
     *
     * @param ingestPort     repository for document ingestion and storage
     * @param webScraperPort port used to scrape URLs into ingestible documents
     */
    public IngestService(IngestPort ingestPort, WebScraperPort webScraperPort) {
        this.ingestPort = ingestPort;
        this.webScraperPort = webScraperPort;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(String documentsPath) {
        try {
            ingestPort.ingestDocuments(documentsPath);
            Log.info("📂 Base documents from '" + documentsPath + "' ingested successfully");
        } catch (Exception e) {
            Log.error("Error ingesting documents", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void executeUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return;
        }
        webScraperPort.clearMarkdownOutputDirIfEnabled();

        List<DocumentData> documents = new ArrayList<>();
        for (String url : urls) {
            webScraperPort.scrapeToDocument(url).ifPresent(documents::add);
        }

        try {
            ingestPort.ingestDocuments(documents);
            Log.info("🌐 Scraped documents from " + documents.size() + "/" + urls.size()
                    + " URL(s) ingested successfully");
        } catch (Exception e) {
            Log.error("Error ingesting scraped URLs", e);
        }
    }
}
