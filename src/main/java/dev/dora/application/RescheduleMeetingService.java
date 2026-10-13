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
import dev.dora.domain.model.TimeSlot;
import dev.dora.domain.port.in.RescheduleMeetingUseCase;
import dev.dora.domain.port.out.CalendarAccessException;
import dev.dora.domain.port.out.CalendarConflictException;
import dev.dora.domain.port.out.CalendarEventNotFoundException;
import dev.dora.domain.port.out.CalendarPort;
import dev.dora.domain.scheduling.SearchWindow;
import dev.dora.domain.scheduling.SlotPlanner;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Moves a Dora meeting onto another valid slot without creating a new event.
 *
 * <p>The current interval of the meeting itself is ignored when checking conflicts.
 * A failed update does not change the event. Plain Java: wiring lives in
 * {@code dev.dora.adapter.config.ApplicationBeans}.
 *
 * @author Rodrigo Prestes Machado
 */
public class RescheduleMeetingService implements RescheduleMeetingUseCase {

    private static final Logger LOG = Logger.getLogger(RescheduleMeetingService.class.getName());

    private final CalendarPort calendarPort;
    private final SchedulingRules rules;
    private final Clock clock;
    private final String officeAddress;
    private final SlotPlanner planner = new SlotPlanner();

    /**
     * Creates the use case.
     *
     * @param calendarPort  calendar provider
     * @param rules         scheduling rules
     * @param clock         source of the current instant
     * @param officeAddress address confirmed again when an in-person meeting is moved
     */
    public RescheduleMeetingService(CalendarPort calendarPort, SchedulingRules rules, Clock clock,
            String officeAddress) {
        this.calendarPort = calendarPort;
        this.rules = rules;
        this.clock = clock;
        this.officeAddress = SchedulingTexts.requireOfficeAddress(officeAddress);
    }

    @Override
    public String reschedule(String eventId, String newStartDateTime) {
        String id = null;
        try {
            id = SchedulingTexts.requireEventId(eventId);
            Instant start = SchedulingTexts.parseStart(newStartDateTime, rules.getTimezone());
            CalendarEvent current = calendarPort.findEvent(id).orElse(null);
            if (current == null) {
                return "Não encontrei essa reunião. Ela pode já ter sido cancelada.";
            }
            String refusal = SchedulingTexts.refusalToChange(current, rules, clock, "reagendar");
            if (refusal != null) {
                return refusal;
            }
            if (current.getStart().equals(start)) {
                return "Esse já é o horário da reunião \"" + current.getTitle()
                        + "\". Não há mudança.";
            }
            List<CalendarEvent> events = loadEvents();
            String rejection = rejection(start, events, id);
            if (rejection != null) {
                return rejection;
            }
            Instant end = start.plus(Duration.ofMinutes(rules.getMeetingDurationMinutes()));
            CalendarEvent updated = calendarPort.updateEventTime(id, start, end,
                    rules.isNotifyAttendees());
            CalendarEvent withLink = updated.getConferenceLink() == null
                    && current.getConferenceLink() != null
                    ? new CalendarEvent(updated.getId(), updated.getTitle(), updated.getStart(),
                            updated.getEnd(), 
                            updated.isAllDay(), updated.getAttendeeEmails(),
                                    current.getConferenceLink(), 
                            updated.isCreatedByDora(), updated.isCancelled(), updated.isTentative(),
                            updated.isShowAsFree())
                    : updated;
            return SchedulingTexts.moved(current, withLink, rules, officeAddress);
        } catch (IllegalArgumentException exception) {
            return exception.getMessage();
        } catch (CalendarEventNotFoundException exception) {
            return "Não encontrei essa reunião. Ela pode já ter sido cancelada.";
        } catch (CalendarConflictException exception) {
            LOG.log(Level.INFO, "O provedor recusou o novo horário", exception);
            return stillBusy(id);
        } catch (CalendarAccessException exception) {
            return SchedulingTexts.unavailable(LOG, exception);
        } catch (RuntimeException exception) {
            return SchedulingTexts.unexpected(LOG, exception);
        }
    }

    private String stillBusy(String eventId) {
        try {
            return "Esse horário não está mais livre. A reunião continua no horário anterior.\n"
                    + SchedulingTexts.alternatives(offered(loadEvents(), eventId), rules);
        } catch (CalendarAccessException exception) {
            return SchedulingTexts.unavailable(LOG, exception);
        } catch (RuntimeException exception) {
            return SchedulingTexts.unexpected(LOG, exception);
        }
    }

    private String rejection(Instant start, List<CalendarEvent> events, String excludeEventId) {
        ZoneId zone = rules.getTimezone();
        Instant dayFrom = start.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant();
        Instant dayTo = start.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant();
        List<TimeSlot> allowed = planner.availableSlots(rules, clock, List.of(), dayFrom, dayTo,
                null);
        if (SchedulingTexts.matching(allowed, start).isEmpty()) {
            return "Esse horário está fora dos dias, das janelas ou da antecedência em que posso "
                    + "marcar. A reunião continua no horário anterior.\n"
                    + SchedulingTexts.alternatives(offered(events, excludeEventId), rules);
        }
        List<TimeSlot> free = planner.availableSlots(rules, clock, events, dayFrom, dayTo,
                excludeEventId);
        if (SchedulingTexts.matching(free, start).isEmpty()) {
            return "Esse horário não está mais livre. A reunião continua no horário anterior.\n"
                    + SchedulingTexts.alternatives(offered(events, excludeEventId), rules);
        }
        return null;
    }

    private List<TimeSlot> offered(List<CalendarEvent> events, String excludeEventId) {
        String excluded = excludeEventId == null ? null : excludeEventId.trim();
        return planner.present(planner.availableSlots(rules, clock, events, null, null,
                excluded), rules);
    }

    private List<CalendarEvent> loadEvents() {
        SearchWindow window = planner.searchWindow(rules, clock, null, null);
        if (window.isEmpty()) {
            return List.of();
        }
        return calendarPort.listEvents(window.getFrom().minus(Duration.ofDays(1)),
                window.getTo().plus(Duration.ofDays(1)));
    }
}
