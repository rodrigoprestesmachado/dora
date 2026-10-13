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

import dev.dora.domain.model.CalendarEvent;
import dev.dora.domain.model.MeetingMode;
import dev.dora.domain.model.NewMeeting;
import dev.dora.domain.port.out.CalendarEventNotFoundException;
import dev.dora.domain.port.out.CalendarPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * In-memory calendar that records what the use cases asked of the port.
 *
 * @author Rodrigo Prestes Machado
 */
final class RecordingCalendarPort implements CalendarPort {

    final List<CalendarEvent> events = new ArrayList<>();

    int createCalls;
    int updateCalls;
    int cancelCalls;

    NewMeeting lastCreated;

    boolean lastCreateNotified;
    boolean lastUpdateNotified;
    boolean lastCancelNotified;

    String lastUpdatedId;
    Instant lastUpdatedStart;

    RuntimeException onRead;
    RuntimeException onWrite;

    @Override
    public List<CalendarEvent> listEvents(Instant from, Instant to) {
        if (onRead != null) {
            throw onRead;
        }
        return List.copyOf(events);
    }

    @Override
    public Optional<CalendarEvent> findEvent(String eventId) {
        if (onRead != null) {
            throw onRead;
        }
        return events.stream().filter(event -> event.getId().equals(eventId)).findFirst();
    }

    @Override
    public CalendarEvent createEvent(NewMeeting meeting, boolean notifyAttendees) {
        createCalls++;
        lastCreateNotified = notifyAttendees;
        if (onWrite != null) {
            throw onWrite;
        }
        lastCreated = meeting;
        String link = meeting.getMode() == MeetingMode.ONLINE ? "https://meet.google.com/abc-defg-hij" : null;
        CalendarEvent created = new CalendarEvent("created-" + createCalls, meeting.getTitle(),
                meeting.getStart(), 
                meeting.getEnd(), false, meeting.getAttendeeEmails(), link, true, false, false,
                        false);
        events.add(created);
        return created;
    }

    @Override
    public CalendarEvent updateEventTime(String eventId, Instant start, Instant end,
            boolean notifyAttendees) {
        updateCalls++;
        lastUpdatedId = eventId;
        lastUpdatedStart = start;
        lastUpdateNotified = notifyAttendees;
        if (onWrite != null) {
            throw onWrite;
        }
        CalendarEvent current = events.stream()
                .filter(event -> event.getId().equals(eventId)).findFirst()
                .orElseThrow(() -> new CalendarEventNotFoundException(eventId));
        return current.withInterval(start, end);
    }

    @Override
    public void cancelEvent(String eventId, boolean notifyAttendees) {
        cancelCalls++;
        lastCancelNotified = notifyAttendees;
        if (onWrite != null) {
            throw onWrite;
        }
        if (events.stream().noneMatch(event -> event.getId().equals(eventId))) {
            throw new CalendarEventNotFoundException(eventId);
        }
    }
}
