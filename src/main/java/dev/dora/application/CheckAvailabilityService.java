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
import dev.dora.domain.port.in.CheckAvailabilityUseCase;
import dev.dora.domain.port.out.CalendarAccessException;
import dev.dora.domain.port.out.CalendarPort;
import dev.dora.domain.scheduling.SearchWindow;
import dev.dora.domain.scheduling.SlotPlanner;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.logging.Logger;

/**
 * Lists free slots by crossing the calendar with the scheduling rules.
 *
 * <p>Plain Java: wiring lives in {@code dev.dora.adapter.config.ApplicationBeans}.
 *
 * @author Rodrigo Prestes Machado
 */
public class CheckAvailabilityService implements CheckAvailabilityUseCase {

    private static final Logger LOG = Logger.getLogger(CheckAvailabilityService.class.getName());

    private final CalendarPort calendarPort;
    private final SchedulingRules rules;
    private final Clock clock;
    private final SlotPlanner planner = new SlotPlanner();

    /**
     * Creates the use case.
     *
     * @param calendarPort calendar provider
     * @param rules        scheduling rules
     * @param clock        source of the current instant
     */
    public CheckAvailabilityService(CalendarPort calendarPort, SchedulingRules rules, Clock clock) {
        this.calendarPort = calendarPort;
        this.rules = rules;
        this.clock = clock;
    }

    @Override
    public String check(String fromDate, String toDate) {
        try {
            Instant from = SchedulingTexts.parseOptionalDate(fromDate, rules.getTimezone(), true);
            Instant to = SchedulingTexts.parseOptionalDate(toDate, rules.getTimezone(), false);
            if (from != null && to != null && !from.isBefore(to)) {
                return "A data final precisa ser igual ou posterior à data inicial.";
            }
            SearchWindow window = planner.searchWindow(rules, clock, from, to);
            if (window.isEmpty()) {
                return SchedulingTexts.outsideWindow(rules, clock, fromDate, toDate);
            }
            List<TimeSlot> slots = freeSlots(from, to, null);
            if (slots.isEmpty()) {
                return SchedulingTexts.NO_SLOTS;
            }
            return SchedulingTexts.availability(slots, rules);
        } catch (IllegalArgumentException exception) {
            return exception.getMessage();
        } catch (CalendarAccessException exception) {
            return SchedulingTexts.unavailable(LOG, exception);
        } catch (RuntimeException exception) {
            return SchedulingTexts.unexpected(LOG, exception);
        }
    }

    private List<TimeSlot> freeSlots(Instant from, Instant to, String excludeEventId) {
        SearchWindow window = planner.searchWindow(rules, clock, from, to);
        List<CalendarEvent> events = window.isEmpty()
                ? List.of()
                : calendarPort.listEvents(window.getFrom().minus(Duration.ofDays(1)),
                        window.getTo());
        return planner.availableSlots(rules, clock, events, from, to, excludeEventId);
    }
}
