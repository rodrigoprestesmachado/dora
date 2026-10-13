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
import java.util.List;

/**
 * Data required to create a meeting. The provider assigns the id and, for an online meeting,
 * the conference link.
 *
 * @author Rodrigo Prestes Machado
 */
public final class NewMeeting {

    private final String title;
    private final Instant start;
    private final Instant end;
    private final List<String> attendeeEmails;
    private final MeetingMode mode;
    private final String location;
    private final String notes;

    /**
     * Creates a meeting request.
     *
     * @param title          meeting title
     * @param start          inclusive UTC start
     * @param end            exclusive UTC end
     * @param attendeeEmails attendee e-mail addresses; required for both modes so the calendar
     *     invitation can be sent
     * @param mode           online or in person
     * @param location       office address for an in-person meeting; ignored when the meeting is
     *     online
     * @param notes          optional subject the client chose to share; blank becomes {@code null}
     */
    public NewMeeting(String title, Instant start, Instant end, List<String> attendeeEmails,
            MeetingMode mode, String location, String notes) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("A reunião precisa de um título.");
        }
        if (start == null || end == null || !start.isBefore(end)) {
            throw new IllegalArgumentException("A reunião precisa ter início antes do fim.");
        }
        if (mode == null) {
            throw new IllegalArgumentException("A reunião precisa ser online ou presencial.");
        }
        List<String> emails = List.copyOf(attendeeEmails == null ? List.of() : attendeeEmails);
        if (emails.isEmpty()) {
            throw new IllegalArgumentException(
                    "A reunião precisa do e-mail de ao menos um participante.");
        }
        if (mode == MeetingMode.IN_PERSON && (location == null || location.isBlank())) {
            throw new IllegalArgumentException(
                    "A reunião presencial precisa do endereço do escritório.");
        }
        this.title = title.trim();
        this.start = start;
        this.end = end;
        this.attendeeEmails = emails;
        this.mode = mode;
        this.location = mode == MeetingMode.IN_PERSON ? location.trim() : null;
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
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

    public List<String> getAttendeeEmails() {
        return attendeeEmails;
    }

    public MeetingMode getMode() {
        return mode;
    }

    /**
     * Returns the location of the meeting, when applicable.
     *
     * @return the office address of an in-person meeting, or {@code null} when the meeting is
     *     online
     */
    public String getLocation() {
        return location;
    }

    /**
     * Returns the subject the client chose to share ahead of the meeting.
     *
     * @return the notes, or {@code null} when the client did not want to advance the subject
     */
    public String getNotes() {
        return notes;
    }
}
