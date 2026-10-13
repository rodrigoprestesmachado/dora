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
import dev.dora.domain.model.SchedulingRules;
import dev.dora.domain.port.in.FindMeetingsUseCase;
import dev.dora.domain.port.out.CalendarAccessException;
import dev.dora.domain.port.out.CalendarPort;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Lists future meetings created by Dora so one of them can be moved or cancelled.
 *
 * <p>Plain Java: wiring lives in {@code dev.dora.adapter.config.ApplicationBeans}.
 *
 * @author Rodrigo Prestes Machado
 */
public class FindMeetingsService implements FindMeetingsUseCase {

    private static final Logger LOG = Logger.getLogger(FindMeetingsService.class.getName());

    /**
     * How far ahead an existing meeting is still looked up. Booking new ones uses a shorter
     * window.
     */
    private static final int LOOKAHEAD_DAYS = 365;

    private final CalendarPort calendarPort;
    private final SchedulingRules rules;
    private final Clock clock;

    /**
     * Creates the use case.
     *
     * @param calendarPort calendar provider
     * @param rules        scheduling rules, used for the time zone and the displayed times
     * @param clock        source of the current instant
     */
    public FindMeetingsService(CalendarPort calendarPort, SchedulingRules rules, Clock clock) {
        this.calendarPort = calendarPort;
        this.rules = rules;
        this.clock = clock;
    }

    @Override
    public String find(String attendeeEmail, String fromDate, String toDate, String titleQuery) {
        try {
            Instant now = clock.instant();
            Instant requestedFrom = SchedulingTexts.parseOptionalDate(fromDate, rules.getTimezone(),
                    true);
            Instant requestedTo = SchedulingTexts.parseOptionalDate(toDate, rules.getTimezone(),
                    false);
            Instant from = requestedFrom == null
                    || requestedFrom.isBefore(now) ? now : requestedFrom;
            Instant to = requestedTo == null
                    ? now.plus(java.time.Duration.ofDays(LOOKAHEAD_DAYS)) : requestedTo;
            if (!from.isBefore(to)) {
                return "Não encontrei reuniões futuras marcadas por mim com esses filtros.";
            }
            String email = blankToNull(attendeeEmail);
            String title = blankToNull(titleQuery);
            List<CalendarEvent> matches = new ArrayList<>();
            for (CalendarEvent event : calendarPort.listEvents(from, to)) {
                if (matches(event, now, from, to, email, title)) {
                    matches.add(event);
                }
            }
            matches.sort(Comparator.comparing(CalendarEvent::getStart));
            if (matches.isEmpty()) {
                return "Não encontrei reuniões futuras marcadas por mim com esses filtros.";
            }
            return format(matches);
        } catch (IllegalArgumentException exception) {
            return exception.getMessage();
        } catch (CalendarAccessException exception) {
            return SchedulingTexts.unavailable(LOG, exception);
        } catch (RuntimeException exception) {
            return SchedulingTexts.unexpected(LOG, exception);
        }
    }

    private boolean matches(CalendarEvent event, Instant now, Instant from, Instant to,
            String email, String title) {
        if (!event.isCreatedByDora() || event.isCancelled() || event.getStart().isBefore(now)) {
            return false;
        }
        if (event.getStart().isBefore(from) || !event.getStart().isBefore(to)) {
            return false;
        }
        if (email != null && event.getAttendeeEmails().stream()
                .noneMatch(attendee -> attendee.equalsIgnoreCase(email))) {
            return false;
        }
        return title == null
                || event.getTitle().toLowerCase(Locale.ROOT)
                        .contains(title.toLowerCase(Locale.ROOT));
    }

    private String format(List<CalendarEvent> matches) {
        String intro = matches.size() == 1
                ? "Encontrei uma reunião futura marcada por mim:"
                : "Encontrei mais de uma reunião futura marcada por mim. Peça ao cliente para "
                        + "indicar qual deseja alterar:";
        StringBuilder text = new StringBuilder(intro);
        int index = 1;
        for (CalendarEvent event : matches) {
            text.append("\n").append(index++).append(". ").append(event.getTitle()).append(" — ")
                    .append(SchedulingTexts.formatInterval(event.getStart(), event.getEnd(),
                            rules.getTimezone()))
                    .append(" — ID: ").append(event.getId()).append(" — participantes: ")
                    .append(String.join(", ", event.getAttendeeEmails())).append(" — link: ")
                    .append(SchedulingTexts.linkOf(event));
        }
        return text.toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
