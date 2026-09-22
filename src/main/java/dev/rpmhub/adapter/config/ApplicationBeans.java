/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */
package dev.rpmhub.adapter.config;

import dev.rpmhub.adapter.out.ai.DoraAgent;
import dev.rpmhub.application.ChatService;
import dev.rpmhub.application.IngestService;
import dev.rpmhub.application.ProcessLookupService;
import dev.rpmhub.domain.port.in.ChatUseCase;
import dev.rpmhub.domain.port.in.IngestDocumentsPort;
import dev.rpmhub.domain.port.in.ProcessLookupUseCase;
import dev.rpmhub.domain.port.out.EmbeddingRepository;
import dev.rpmhub.domain.port.out.IngestPort;
import dev.rpmhub.domain.port.out.ProcessLookupPort;
import dev.rpmhub.domain.port.out.Repository;
import dev.rpmhub.domain.port.out.WebScraperPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * CDI wiring for the application layer.
 *
 * <p>The {@code application} package is kept free of framework annotations so
 * its classes remain plain Java and easy to unit test. This class is the only
 * place responsible for instantiating application services and exposing them
 * as CDI beans through their driving ports.
 *
 * @author Rodrigo Prestes Machado
 */
@ApplicationScoped
public class ApplicationBeans {

    @Inject
    Repository chatRepository;

    /** Port for embedding-based retrieval, used by the RAG use cases. */
    @Inject
    EmbeddingRepository embeddingRepository;

    /** Port for document ingestion into the embedding store. */
    @Inject
    IngestPort ingestPort;

    /** Port used to scrape URLs into ingestible documents. */
    @Inject
    WebScraperPort webScraperPort;

    /** Port used to look up a legal process by its CNJ number (e.g. TJRS). */
    @Inject
    ProcessLookupPort processLookupPort;

    /** LangChain4j AI service that streams replies grounded in RAG-retrieved context. */
    @Inject
    DoraAgent doraAgent;

    /** Number of context chunks retrieved per message. */
    @ConfigProperty(name = "rag.max-results", defaultValue = "3")
    int ragMaxResults;

    /** Minimum similarity score required for a retrieved chunk to be used as context. */
    @ConfigProperty(name = "rag.min-score", defaultValue = "0.6")
    double ragMinScore;

    /** Maximum idle time, in minutes, between user messages before a new chat session starts. */
    @ConfigProperty(name = "chat.inactivity-threshold-minutes", defaultValue = "30")
    long chatInactivityThresholdMinutes;

    /**
     * Produces the {@link ChatUseCase} bean backed by a plain {@link ChatService}.
     *
     * @return the chat use case implementation
     */
    @Produces
    @ApplicationScoped
    public ChatUseCase chatUseCase() {
        long inactivityThresholdMs = chatInactivityThresholdMinutes * 60_000L;
        return new ChatService(chatRepository, embeddingRepository, doraAgent, ragMaxResults, ragMinScore,
                inactivityThresholdMs);
    }

    /**
     * Produces the {@link IngestDocumentsPort} bean backed by a plain {@link IngestService}.
     *
     * @return the local-directory ingestion use case implementation
     */
    @Produces
    @ApplicationScoped
    public IngestDocumentsPort ingestDocumentsPort() {
        return new IngestService(ingestPort, webScraperPort);
    }

    /**
     * Produces the {@link ProcessLookupUseCase} bean backed by a plain {@link ProcessLookupService}.
     *
     * @return the process lookup use case implementation, consumed by {@code DoraTools}
     */
    @Produces
    @ApplicationScoped
    public ProcessLookupUseCase processLookupUseCase() {
        return new ProcessLookupService(processLookupPort);
    }

}
