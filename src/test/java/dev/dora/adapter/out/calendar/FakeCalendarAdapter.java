/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */

package dev.dora.adapter.out.calendar;

import dev.dora.domain.model.CalendarEvent;
import dev.dora.domain.model.NewMeeting;
import dev.dora.domain.port.out.CalendarAccessException;
import dev.dora.domain.port.out.CalendarPort;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Calendar used by {@code @QuarkusTest} so the application can boot without the Google MCP server.
 *
 * @author Rodrigo Prestes Machado
 */
@IfBuildProperty(name = "scheduling.calendar-provider", stringValue = "fake")
@ApplicationScoped
public class FakeCalendarAdapter implements CalendarPort {

    @Override
    public List<CalendarEvent> listEvents(Instant from, Instant to) {
        return List.of();
    }

    @Override
    public Optional<CalendarEvent> findEvent(String eventId) {
        return Optional.empty();
    }

    @Override
    public CalendarEvent createEvent(NewMeeting meeting, boolean notifyAttendees) {
        throw new CalendarAccessException("A agenda de teste não grava eventos.");
    }

    @Override
    public CalendarEvent updateEventTime(String eventId, Instant start, Instant end,
            boolean notifyAttendees) {
        throw new CalendarAccessException("A agenda de teste não grava eventos.");
    }

    @Override
    public void cancelEvent(String eventId, boolean notifyAttendees) {
        throw new CalendarAccessException("A agenda de teste não grava eventos.");
    }
}
