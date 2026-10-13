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

import dev.dora.adapter.out.ai.DoraAgent;
import dev.dora.application.CancelMeetingService;
import dev.dora.application.ChatService;
import dev.dora.application.CheckAvailabilityService;
import dev.dora.application.FindMeetingsService;
import dev.dora.application.IngestService;
import dev.dora.application.ProcessLookupService;
import dev.dora.application.RescheduleMeetingService;
import dev.dora.application.ScheduleMeetingService;
import dev.dora.application.SuggestMeetingSlotsService;
import dev.dora.domain.model.SchedulingRules;
import dev.dora.domain.port.in.CancelMeetingUseCase;
import dev.dora.domain.port.in.ChatUseCase;
import dev.dora.domain.port.in.CheckAvailabilityUseCase;
import dev.dora.domain.port.in.FindMeetingsUseCase;
import dev.dora.domain.port.in.IngestDocumentsPort;
import dev.dora.domain.port.in.ProcessLookupUseCase;
import dev.dora.domain.port.in.RescheduleMeetingUseCase;
import dev.dora.domain.port.in.ScheduleMeetingUseCase;
import dev.dora.domain.port.in.SuggestMeetingSlotsUseCase;
import dev.dora.domain.port.out.CalendarPort;
import dev.dora.domain.port.out.ClientNoticePort;
import dev.dora.domain.port.out.EmbeddingRepository;
import dev.dora.domain.port.out.IngestPort;
import dev.dora.domain.port.out.ProcessLookupPort;
import dev.dora.domain.port.out.Repository;
import dev.dora.domain.port.out.WebScraperPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.ZoneId;
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

    /** Port used to warn the client before a TJRS consultation is retried. */
    @Inject
    ClientNoticePort clientNotice;

    /** Active calendar provider, selected by {@code scheduling.calendar-provider}. */
    @Inject
    CalendarPort calendarPort;

    /** Typed scheduling configuration, mapped into {@link SchedulingRules} for the use cases. */
    @Inject
    SchedulingConfig schedulingConfig;

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
        return new ChatService(chatRepository, embeddingRepository, doraAgent, ragMaxResults,
                ragMinScore, 
                inactivityThresholdMs, Clock.system(ZoneId.of(schedulingConfig.timezone())));
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
     * Produces the {@link ProcessLookupUseCase} bean backed by a plain {@link
     * ProcessLookupService}.
     *
     * @return the process lookup use case implementation, consumed by {@code DoraTools}
     */
    @Produces
    @ApplicationScoped
    public ProcessLookupUseCase processLookupUseCase() {
        return new ProcessLookupService(processLookupPort, clientNotice);
    }

    /**
     * Produces the {@link CheckAvailabilityUseCase} bean.
     *
     * @return the availability use case
     */
    @Produces
    @ApplicationScoped
    public CheckAvailabilityUseCase checkAvailabilityUseCase() {
        return new CheckAvailabilityService(calendarPort, schedulingRules(), clock());
    }

    /**
     * Produces the {@link SuggestMeetingSlotsUseCase} bean.
     *
     * @return the slot suggestion use case
     */
    @Produces
    @ApplicationScoped
    public SuggestMeetingSlotsUseCase suggestMeetingSlotsUseCase() {
        return new SuggestMeetingSlotsService(calendarPort, schedulingRules(), clock());
    }

    /**
     * Produces the {@link ScheduleMeetingUseCase} bean.
     *
     * @return the booking use case
     */
    @Produces
    @ApplicationScoped
    public ScheduleMeetingUseCase scheduleMeetingUseCase() {
        return new ScheduleMeetingService(calendarPort, schedulingRules(), clock(),
                schedulingConfig.officeAddress());
    }

    /**
     * Produces the {@link FindMeetingsUseCase} bean.
     *
     * @return the meeting search use case
     */
    @Produces
    @ApplicationScoped
    public FindMeetingsUseCase findMeetingsUseCase() {
        return new FindMeetingsService(calendarPort, schedulingRules(), clock());
    }

    /**
     * Produces the {@link RescheduleMeetingUseCase} bean.
     *
     * @return the reschedule use case
     */
    @Produces
    @ApplicationScoped
    public RescheduleMeetingUseCase rescheduleMeetingUseCase() {
        return new RescheduleMeetingService(calendarPort, schedulingRules(), clock(),
                schedulingConfig.officeAddress());
    }

    /**
     * Produces the {@link CancelMeetingUseCase} bean.
     *
     * @return the cancellation use case
     */
    @Produces
    @ApplicationScoped
    public CancelMeetingUseCase cancelMeetingUseCase() {
        return new CancelMeetingService(calendarPort, schedulingRules(), clock());
    }

    private SchedulingRules schedulingRules() {
        return SchedulingRulesFactory.create(schedulingConfig);
    }

    private Clock clock() {
        return Clock.system(schedulingRules().getTimezone());
    }

}
