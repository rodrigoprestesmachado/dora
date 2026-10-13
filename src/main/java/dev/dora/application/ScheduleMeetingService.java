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
import dev.dora.domain.model.SchedulingRules;
import dev.dora.domain.model.TimeSlot;
import dev.dora.domain.port.in.ScheduleMeetingUseCase;
import dev.dora.domain.port.out.CalendarAccessException;
import dev.dora.domain.port.out.CalendarConflictException;
import dev.dora.domain.port.out.CalendarPort;
import dev.dora.domain.scheduling.SearchWindow;
import dev.dora.domain.scheduling.SlotPlanner;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Books a meeting after rechecking that the chosen slot is still free and allowed.
 *
 * <p>Plain Java: wiring lives in {@code dev.dora.adapter.config.ApplicationBeans}.
 *
 * @author Rodrigo Prestes Machado
 */
public class ScheduleMeetingService implements ScheduleMeetingUseCase {

    private static final Logger LOG = Logger.getLogger(ScheduleMeetingService.class.getName());

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
     * @param officeAddress address stored on in-person meetings and told to the client
     */
    public ScheduleMeetingService(CalendarPort calendarPort, SchedulingRules rules, Clock clock,
            String officeAddress) {
        this.calendarPort = calendarPort;
        this.rules = rules;
        this.clock = clock;
        this.officeAddress = SchedulingTexts.requireOfficeAddress(officeAddress);
    }

    @Override
    public String schedule(String startDateTime, String title, String attendeeEmails, String mode,
            String notes) {
        try {
            String meetingTitle = SchedulingTexts.requireTitle(title);
            MeetingMode meetingMode = SchedulingTexts.parseMode(mode);
            List<String> emails = SchedulingTexts.parseEmails(attendeeEmails);
            Instant start = SchedulingTexts.parseStart(startDateTime, rules.getTimezone());
            Instant end = start.plus(Duration.ofMinutes(rules.getMeetingDurationMinutes()));
            List<CalendarEvent> events = loadEvents();
            Optional<CalendarEvent> existing = events.stream()
                    .filter(event -> SchedulingTexts.sameBooking(event, start, meetingTitle,
                            emails))
                    .findFirst();
            if (existing.isPresent()) {
                return SchedulingTexts.booked(existing.get(), rules, true, meetingMode,
                        officeAddress, null);
            }
            String rejection = rejection(start, events, null);
            if (rejection != null) {
                return rejection;
            }
            String location = meetingMode == MeetingMode.IN_PERSON ? officeAddress : null;
            CalendarEvent created = calendarPort.createEvent(
                    new NewMeeting(meetingTitle, start, end, emails, meetingMode,
                            location, notes), rules.isNotifyAttendees());
            return SchedulingTexts.booked(created, rules, false, meetingMode, officeAddress,
                    notes);
        } catch (IllegalArgumentException exception) {
            return exception.getMessage();
        } catch (CalendarConflictException exception) {
            LOG.log(java.util.logging.Level.INFO, "O provedor recusou o horário escolhido",
                    exception);
            return occupied();
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
            return "Esse horário está fora dos dias, das janelas ou da antecedência em que "
                    + "posso marcar.\n"
                    + SchedulingTexts.alternatives(offered(events, excludeEventId), rules);
        }
        List<TimeSlot> free = planner.availableSlots(rules, clock, events, dayFrom, dayTo,
                excludeEventId);
        if (SchedulingTexts.matching(free, start).isEmpty()) {
            return "Esse horário não está mais livre. Não marquei a reunião.\n"
                    + SchedulingTexts.alternatives(offered(events, excludeEventId), rules);
        }
        return null;
    }

    private String occupied() {
        try {
            return "Esse horário não está mais livre. Não marquei a reunião.\n"
                    + SchedulingTexts.alternatives(offered(loadEvents(), null), rules);
        } catch (CalendarAccessException exception) {
            return SchedulingTexts.unavailable(LOG, exception);
        } catch (RuntimeException exception) {
            return SchedulingTexts.unexpected(LOG, exception);
        }
    }

    private List<TimeSlot> offered(List<CalendarEvent> events, String excludeEventId) {
        return planner.present(planner.availableSlots(rules, clock, events, null, null,
                excludeEventId), rules);
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
