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

import dev.dora.domain.model.CalendarEvent;
import dev.dora.domain.model.NewMeeting;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Driven port for one calendar provider (Google Calendar, and later others such as Apple Calendar).
 *
 * <p>Implementations translate these operations into the provider's API. They mark events
 * they create so {@link CalendarEvent#isCreatedByDora()} can be answered without the
 * domain knowing the provider's storage. Updating the time keeps the same event id and
 * the same conference link. A failed update or cancellation leaves the original event intact.
 *
 * @author Rodrigo Prestes Machado
 */
public interface CalendarPort {

    /**
     * Lists events that overlap {@code [from, to)}.
     *
     * @param from inclusive range start, UTC
     * @param to   exclusive range end, UTC
     * @return the events in that range, including all-day, provisional and free events
     * @throws CalendarAccessException if the provider could not be reached
     */
    List<CalendarEvent> listEvents(Instant from, Instant to);

    /**
     * Loads one event by id.
     *
     * @param eventId provider event id
     * @return the event, or empty when it does not exist or was already removed
     * @throws CalendarAccessException if the provider could not be reached
     */
    Optional<CalendarEvent> findEvent(String eventId);

    /**
     * Creates an event and marks it as created by Dora. An online meeting includes a
     * video-conference
     * link; an in-person meeting stores the office address and does not request a link.
     *
     * @param meeting          title, interval, attendees and whether it is online or in person
     * @param notifyAttendees  whether the provider should e-mail the attendees
     * @return the created event, including its id and conference link when the meeting is online
     * @throws CalendarConflictException if the provider rejects the time as taken
     * @throws CalendarAccessException   if the provider could not be reached
     */
    CalendarEvent createEvent(NewMeeting meeting, boolean notifyAttendees);

    /**
     * Moves an existing event. Does not create a second event and does not replace the conference
     * link.
     *
     * @param eventId         id of the event to move
     * @param start           new inclusive UTC start
     * @param end             new exclusive UTC end
     * @param notifyAttendees whether the provider should e-mail the attendees
     * @return the updated event, with the same id
     * @throws CalendarEventNotFoundException if the event is already gone
     * @throws CalendarConflictException      if the provider rejects the new time
     * @throws CalendarAccessException        if the provider could not be reached; the original
     *     event stays
     */
    CalendarEvent updateEventTime(String eventId, Instant start, Instant end,
            boolean notifyAttendees);

    /**
     * Cancels an existing event. Repeating the call for an event that is already gone
     * is reported as not found, not as a new cancellation.
     *
     * @param eventId         id of the event to cancel
     * @param notifyAttendees whether the provider should e-mail the attendees
     * @throws CalendarEventNotFoundException if the event is already gone
     * @throws CalendarAccessException        if the provider could not be reached; the original
     *     event stays
     */
    void cancelEvent(String eventId, boolean notifyAttendees);
}
