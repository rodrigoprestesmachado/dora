/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */

package dev.dora.adapter.out.ai;

import dev.dora.adapter.ClientProgress;
import dev.dora.adapter.ConversationPhone;
import dev.dora.domain.port.in.CancelMeetingUseCase;
import dev.dora.domain.port.in.CheckAvailabilityUseCase;
import dev.dora.domain.port.in.FindMeetingsUseCase;
import dev.dora.domain.port.in.ProcessLookupUseCase;
import dev.dora.domain.port.in.RescheduleMeetingUseCase;
import dev.dora.domain.port.in.ScheduleMeetingUseCase;
import dev.dora.domain.port.in.SuggestMeetingSlotsUseCase;
import dev.dora.domain.port.out.ClientNoticePort;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.function.Supplier;

/**
 * LangChain4j tools exposed to {@link DoraAgent}. Each method is a thin adapter
 * that delegates to a driving port (use case). The text the model sees lives in
 * {@code prompts/dora-tools.txt} and is registered by {@link DoraToolCatalog}.
 *
 * @author Rodrigo Prestes Machado
 */
@ApplicationScoped
public class DoraTools {

    /** Use case that looks up a legal process on the TJRS website. */
    private final ProcessLookupUseCase processLookupUseCase;

    /** Use case that lists free meeting times. */
    private final CheckAvailabilityUseCase checkAvailabilityUseCase;

    /** Use case that chooses which free times to show. */
    private final SuggestMeetingSlotsUseCase suggestMeetingSlotsUseCase;

    /** Use case that books a meeting. */
    private final ScheduleMeetingUseCase scheduleMeetingUseCase;

    /** Use case that finds meetings Dora already booked. */
    private final FindMeetingsUseCase findMeetingsUseCase;

    /** Use case that moves a meeting Dora booked. */
    private final RescheduleMeetingUseCase rescheduleMeetingUseCase;

    /** Use case that cancels a meeting Dora booked. */
    private final CancelMeetingUseCase cancelMeetingUseCase;

    /** Port used to tell the client that a TJRS consultation is still running. */
    private final ClientNoticePort clientNotice;

    /**
     * Creates DoraTools with the given use cases.
     *
     * @param processLookupUseCase       use case used to look up a legal process by number or by
     *     person
     * @param checkAvailabilityUseCase   use case that lists free meeting times
     * @param suggestMeetingSlotsUseCase use case that chooses which free times to show
     * @param scheduleMeetingUseCase     use case that books a meeting
     * @param findMeetingsUseCase        use case that finds meetings Dora already booked
     * @param rescheduleMeetingUseCase   use case that moves a meeting Dora booked
     * @param cancelMeetingUseCase       use case that cancels a meeting Dora booked
     * @param clientNotice               port used to send a progress update while the TJRS
     *     consultation runs
     */
    @Inject
    public DoraTools(ProcessLookupUseCase processLookupUseCase,
            CheckAvailabilityUseCase checkAvailabilityUseCase, 
            SuggestMeetingSlotsUseCase suggestMeetingSlotsUseCase,
                    ScheduleMeetingUseCase scheduleMeetingUseCase, 
            FindMeetingsUseCase findMeetingsUseCase,
                    RescheduleMeetingUseCase rescheduleMeetingUseCase, 
            CancelMeetingUseCase cancelMeetingUseCase, ClientNoticePort clientNotice) {
        this.processLookupUseCase = processLookupUseCase;
        this.checkAvailabilityUseCase = checkAvailabilityUseCase;
        this.suggestMeetingSlotsUseCase = suggestMeetingSlotsUseCase;
        this.scheduleMeetingUseCase = scheduleMeetingUseCase;
        this.findMeetingsUseCase = findMeetingsUseCase;
        this.rescheduleMeetingUseCase = rescheduleMeetingUseCase;
        this.cancelMeetingUseCase = cancelMeetingUseCase;
        this.clientNotice = clientNotice;
    }

    /**
     * Looks up the current status of a legal process at TJRS (Tribunal de
     * Justiça do Rio Grande do Sul, the Rio Grande do Sul state court) by its
     * CNJ process number.
     *
     * <p>Use this whenever a client asks about the status, progress or latest
     * movement of a legal case and provides (or can provide) the process
     * number. Ask the client for the process number first if they have not
     * given one; it should look like {@code 5033013-66.2026.8.21.0022}.
     *
     * @param processNumber the CNJ process number, with or without punctuation
     * @param memoryId chat memory id (the client's phone), hidden from the model
     * @return Markdown with the process status and its latest movement, or a clear explanation
     *         if the number is invalid, the process was not found, or the lookup failed
     */
    public String lookupTjrsProcess(String processNumber, Object memoryId) {
        Log.info("🔧 [TOOL CALL] lookupTjrsProcess invoked by the agent. processNumber="
                + processNumber);
        String result = duringConversation(memoryId,
                () -> processLookupUseCase.lookup(processNumber));
        Log.info("🔧 [TOOL CALL] lookupTjrsProcess finished. processNumber=" + processNumber);
        return result;
    }

    /**
     * Lists TJRS processes for a person identified by exact name.
     *
     * <p>Call this when the client asks about a case and gives a person's name
     * instead of the CNJ number. The court has no CPF search, so this tool does
     * not take one. A single exact match returns that person's processes; several
     * records with the same name return the people so the client can say which
     * one. After the client chooses a process number, call
     * {@link #lookupTjrsProcess(String, Object)}.
     *
     * @param personName the person's name (first name and surname)
     * @param memoryId chat memory id (the client's phone), hidden from the model
     * @return Markdown listing people or processes, or a clear explanation if the
     *         input is invalid, nothing was found, or the search failed
     */
    public String searchTjrsProcessesByPerson(String personName, Object memoryId) {
        Log.info("🔧 [TOOL CALL] searchTjrsProcessesByPerson invoked by the agent. personName="
                + personName);
        String result = duringConversation(memoryId,
                () -> processLookupUseCase.searchByPerson(personName));
        Log.info("🔧 [TOOL CALL] searchTjrsProcessesByPerson finished. personName=" + personName);
        return result;
    }

    /**
     * Binds the chat memory id (the client's phone) to this thread while the tool runs,
     * and sends a short update every ten seconds until the consultation returns.
     */
    private String duringConversation(Object memoryId, Supplier<String> call) {
        String phone = memoryId == null ? null : memoryId.toString();
        ConversationPhone.set(phone);
        try (ClientProgress progress = ClientProgress.everyTenSeconds(phone, clientNotice)) {
            return call.get();
        } finally {
            ConversationPhone.clear();
        }
    }

    /**
     * Lists free meeting times inside the office rules. Never offers a time outside
     * the allowed days, hours or search window, nor a time that overlaps an existing event.
     *
     * @param fromDate inclusive start date ({@code AAAA-MM-DD}), or blank to start today
     * @param toDate   inclusive end date ({@code AAAA-MM-DD}), or blank to use the configured
     *     window
     * @return the free times, or an explanation when there are none
     */
    public String checkAvailability(String fromDate, String toDate) {
        Log.info("🔧 [TOOL CALL] checkAvailability invoked. from=" + fromDate + " to=" + toDate);
        return checkAvailabilityUseCase.check(fromDate, toDate);
    }

    /**
     * Offers the slots the user should see. Also used before moving a meeting:
     * pass the event id so its current time is not treated as busy.
     *
     * @param fromDate       inclusive start date, or blank
     * @param toDate         inclusive end date, or blank
     * @param excludeEventId event being moved, or blank when booking a new meeting
     * @return the options, and whether to wait for a choice or book the earliest slot
     */
    public String suggestMeetingSlots(String fromDate, String toDate, String excludeEventId) {
        Log.info("🔧 [TOOL CALL] suggestMeetingSlots invoked. from=" + fromDate + " to=" + toDate
                + " exclude=" + excludeEventId);
        return suggestMeetingSlotsUseCase.suggest(fromDate, toDate, excludeEventId);
    }

    /**
     * Books the chosen slot. The duration and the Dora mark come from the use case.
     * An online meeting also receives a video link; an in-person meeting stores the office address.
     *
     * @param startDateTime  start returned by a suggestion, {@code AAAA-MM-DDTHH:mm}
     * @param title          meeting title, the name of the person who asked
     * @param attendeeEmails attendee addresses; required for online and in-person meetings
     * @param mode           {@code online} or {@code presencial}
     * @param notes          subject the client chose to share; blank when they declined
     * @return confirmation with date, time, time zone and either the link or the office address
     */
    public String scheduleMeeting(String startDateTime, String title, List<String> attendeeEmails,
            String mode, String notes) {
        Log.info("🔧 [TOOL CALL] scheduleMeeting invoked. start=" + startDateTime
                + " title=" + title + " mode=" + mode);
        return scheduleMeetingUseCase.schedule(startDateTime, title,
                joinEmails(attendeeEmails), mode, notes);
    }

    private static String joinEmails(List<String> attendeeEmails) {
        if (attendeeEmails == null || attendeeEmails.isEmpty()) {
            return "";
        }
        return String.join(",", attendeeEmails);
    }

    /**
     * Finds future meetings created by Dora.
     *
     * @param attendeeEmail optional attendee filter
     * @param fromDate      optional inclusive start date
     * @param toDate        optional inclusive end date
     * @param titleQuery    optional fragment of the title
     * @return matching meetings; when there are several, the assistant must ask which one
     */
    public String findMeetings(String attendeeEmail, String fromDate, String toDate,
            String titleQuery) {
        Log.info("🔧 [TOOL CALL] findMeetings invoked.");
        return findMeetingsUseCase.find(attendeeEmail, fromDate, toDate, titleQuery);
    }

    /**
     * Moves a Dora meeting. The id and the conference link stay the same.
     *
     * @param eventId          id from {@link #findMeetings} or {@link #scheduleMeeting}
     * @param newStartDateTime a start offered by {@link #suggestMeetingSlots}
     * @return the previous time, the new time, the time zone, the same id and either the link
     *     or the office address
     */
    public String rescheduleMeeting(String eventId, String newStartDateTime) {
        Log.info("🔧 [TOOL CALL] rescheduleMeeting invoked. eventId=" + eventId + " start="
                + newStartDateTime);
        return rescheduleMeetingUseCase.reschedule(eventId, newStartDateTime);
    }

    /**
     * Cancels a Dora meeting. The assistant must confirm with the user before passing {@code true}.
     *
     * @param eventId         id from {@link #findMeetings} or {@link #scheduleMeeting}
     * @param reason          optional reason
     * @param confirmedByUser true only after the user explicitly agreed, having heard the
     *     title and the time
     * @return confirmation of the cancelled meeting, or a request to confirm first
     */
    public String cancelMeeting(String eventId, String reason, boolean confirmedByUser) {
        Log.info("🔧 [TOOL CALL] cancelMeeting invoked. eventId=" + eventId + " confirmed="
                + confirmedByUser);
        return cancelMeetingUseCase.cancel(eventId, reason, confirmedByUser);
    }
}
