/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */

package dev.dora.adapter.config;

import dev.dora.domain.port.in.IngestDocumentsPort;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Runs local-directory document ingestion (using the configured {@code rag.location}) and,
 * when configured, URL scraping ingestion (using {@code rag.scrape.urls}) at startup.
 *
 * @author Rodrigo Prestes Machado
 */
@ApplicationScoped
public class IngestDocumentsStartup {

    /** Port used to trigger the document-ingestion use case. */
    private final IngestDocumentsPort ingestDocumentsPort;

    /** File-system path to the directory containing the base documents to ingest. */
    @ConfigProperty(name = "rag.location")
    Path documentsPath;

    /** URLs to scrape and ingest at startup, if configured. */
    @ConfigProperty(name = "rag.scrape.urls")
    Optional<List<String>> scrapeUrls;

    /**
     * Constructs the observer with the required ingestion port.
     *
     * @param ingestDocumentsPort port that orchestrates document ingestion
     */
    @Inject
    public IngestDocumentsStartup(IngestDocumentsPort ingestDocumentsPort) {
        this.ingestDocumentsPort = ingestDocumentsPort;
    }

    /**
     * Triggers document ingestion from the configured directory and, if any URLs are
     * configured, scrapes and ingests them too, when the application starts.
     *
     * @param event CDI startup event (unused, only signals application readiness)
     */
    void onStartup(@Observes StartupEvent event) {
        try {
            ingestDocumentsPort.execute(documentsPath.toString());
        } catch (Exception e) {
            Log.error("Error ingesting documents at startup", e);
        }

        List<String> urls = scrapeUrls.orElse(List.of());
        if (!urls.isEmpty()) {
            try {
                ingestDocumentsPort.executeUrls(urls);
            } catch (Exception e) {
                Log.error("Error scraping and ingesting URLs at startup", e);
            }
        }
    }
}
