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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A calendar event as the scheduling rules see it, independent of the provider.
 *
 * <p>Instants are UTC. An all-day event uses the configured time zone's local
 * midnights, with {@code end} exclusive. {@code createdByDora} is a provider-neutral
 * flag: each adapter decides how that mark is stored.
 *
 * @author Rodrigo Prestes Machado
 */
public final class CalendarEvent {

    private final String id;
    private final String title;
    private final Instant start;
    private final Instant end;
    private final boolean allDay;
    private final List<String> attendeeEmails;
    private final String conferenceLink;
    private final boolean createdByDora;
    private final boolean cancelled;
    private final boolean tentative;
    private final boolean showAsFree;

    /**
     * Creates a calendar event.
     *
     * @param id              provider event id
     * @param title           event title, possibly empty
     * @param start           inclusive UTC start
     * @param end             exclusive UTC end
     * @param allDay          whether the event blocks whole local days
     * @param attendeeEmails  attendee e-mail addresses
     * @param conferenceLink  video-conference link, or {@code null} when there is none
     * @param createdByDora   whether this agent created the event
     * @param cancelled       whether the provider already cancelled it
     * @param tentative       whether the event is provisional and still blocks time
     * @param showAsFree      whether the event is marked free and does not block time
     */
    public CalendarEvent(String id, String title, Instant start, Instant end, boolean allDay,
            List<String> attendeeEmails, String conferenceLink, boolean createdByDora,
                    boolean cancelled, 
            boolean tentative, boolean showAsFree) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("O evento precisa de um id.");
        }
        if (start == null || end == null || !start.isBefore(end)) {
            throw new IllegalArgumentException("O evento " + id
                    + " precisa ter início antes do fim.");
        }
        this.id = id;
        this.title = title == null ? "" : title;
        this.start = start;
        this.end = end;
        this.allDay = allDay;
        this.attendeeEmails = List.copyOf(attendeeEmails == null ? List.of() : attendeeEmails);
        this.conferenceLink = conferenceLink == null
                || conferenceLink.isBlank() ? null : conferenceLink;
        this.createdByDora = createdByDora;
        this.cancelled = cancelled;
        this.tentative = tentative;
        this.showAsFree = showAsFree;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Instant getStart() {
        return start;
    }

    public Instant getEnd() {
        return end;
    }

    public boolean isAllDay() {
        return allDay;
    }

    public List<String> getAttendeeEmails() {
        return attendeeEmails;
    }

    public String getConferenceLink() {
        return conferenceLink;
    }

    public boolean isCreatedByDora() {
        return createdByDora;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public boolean isTentative() {
        return tentative;
    }

    public boolean isShowAsFree() {
        return showAsFree;
    }

    /**
     * Returns a copy with a new interval, keeping the id, the link and the Dora mark.
     *
     * @param newStart new inclusive UTC start
     * @param newEnd   new exclusive UTC end
     * @return the moved event
     */
    public CalendarEvent withInterval(Instant newStart, Instant newEnd) {
        return new CalendarEvent(id, title, newStart, newEnd, allDay,
                new ArrayList<>(attendeeEmails), conferenceLink, 
                createdByDora, cancelled, tentative, showAsFree);
    }
}
