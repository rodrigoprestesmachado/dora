/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */

package dev.dora.domain.scheduling;

import dev.dora.domain.model.CalendarEvent;
import dev.dora.domain.model.SchedulingRules;
import dev.dora.domain.model.TimeSlot;
import dev.dora.domain.model.TimeWindow;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Calculates free meeting slots from the rules, the clock and the events already on the calendar.
 *
 * <p>The grid step is the configured duration, aligned to the start of each allowed window.
 * All-day events that occupy time block every local day they cover. Provisional events occupy
 * their interval. Events marked free, cancelled events, and the event being moved do not.
 *
 * @author Rodrigo Prestes Machado
 */
public final class SlotPlanner {

    /**
     * Resolves the interval of allowed start instants.
     *
     * <p>The start is the later of {@code now + min notice} and the requested start.
     * The end is the earlier of the requested end and the start of the local day
     * {@code search-window-days} after today. A missing bound uses that default.
     *
     * @param rules         scheduling rules
     * @param clock         source of "now"; its zone is ignored in favour of the rules
     * @param requestedFrom requested range start, or {@code null} for the default
     * @param requestedTo   requested range end, exclusive, or {@code null} for the default
     * @return the clamped window; it may be empty
     */
    public SearchWindow searchWindow(SchedulingRules rules, java.time.Clock clock,
            Instant requestedFrom, 
            Instant requestedTo) {
        ZoneId zone = rules.getTimezone();
        ZonedDateTime now = clock.instant().atZone(zone);
        ZonedDateTime earliest = now.plusHours(rules.getMinNoticeHours());
        ZonedDateTime horizonEnd = now.toLocalDate().atStartOfDay(zone)
                .plusDays(rules.getSearchWindowDays());

        ZonedDateTime from = requestedFrom == null ? earliest : requestedFrom.atZone(zone);
        if (from.isBefore(earliest)) {
            from = earliest;
        }
        ZonedDateTime to = requestedTo == null ? horizonEnd : requestedTo.atZone(zone);
        if (to.isAfter(horizonEnd)) {
            to = horizonEnd;
        }
        return new SearchWindow(from.toInstant(), to.toInstant());
    }

    /**
     * Lists every free slot inside the search window, in chronological order.
     *
     * @param rules          scheduling rules
     * @param clock          source of "now"
     * @param events         events returned by the calendar; may include events outside the window
     * @param requestedFrom  optional range start
     * @param requestedTo    optional exclusive range end
     * @param excludeEventId id of the event being moved, ignored as a conflict; may be {@code null}
     * @return free slots whose start lies inside the window
     */
    public List<TimeSlot> availableSlots(SchedulingRules rules, java.time.Clock clock,
            List<CalendarEvent> events, 
            Instant requestedFrom, Instant requestedTo, String excludeEventId) {
        SearchWindow window = searchWindow(rules, clock, requestedFrom, requestedTo);
        if (window.isEmpty()) {
            return List.of();
        }
        ZoneId zone = rules.getTimezone();
        ZonedDateTime from = window.getFrom().atZone(zone);
        ZonedDateTime to = window.getTo().atZone(zone);
        List<CalendarEvent> occupying = occupying(events, excludeEventId);

        List<TimeSlot> slots = new ArrayList<>();
        for (LocalDate day = from.toLocalDate(); !day.isAfter(to.toLocalDate());
                day = day.plusDays(1)) {
            if (!rules.getAvailableWeekdays().contains(day.getDayOfWeek())) {
                continue;
            }
            if (allDayBlocks(occupying, day, zone)) {
                continue;
            }
            for (TimeWindow hours : rules.getAvailableHours()) {
                addWindowSlots(rules, zone, day, hours, window, occupying, slots);
            }
        }
        return List.copyOf(slots);
    }

    /**
     * Picks the slots that should be shown.
     *
     * <p>When the user does not choose, only the earliest slot is returned. Otherwise up to
     * {@code maxOptionsToPresent} slots are returned. The earliest start of every configured
     * window is kept first, so an afternoon window is not hidden behind more mornings on later
     * days. The remaining places are the next earliest slots.
     *
     * @param chronologicalSlots free slots, earliest first
     * @param rules              scheduling rules
     * @return the slots to present, in chronological order
     */
    public List<TimeSlot> present(List<TimeSlot> chronologicalSlots, SchedulingRules rules) {
        if (chronologicalSlots == null || chronologicalSlots.isEmpty()) {
            return List.of();
        }
        if (!rules.isPresentOptionsToUser()) {
            return List.of(chronologicalSlots.get(0));
        }
        int max = rules.getMaxOptionsToPresent();
        ZoneId zone = rules.getTimezone();
        Set<TimeSlot> chosen = new LinkedHashSet<>();
        Set<LocalTime> openedWindows = new LinkedHashSet<>();
        for (TimeSlot slot : chronologicalSlots) {
            if (chosen.size() >= max) {
                break;
            }
            if (openedWindows.add(windowStart(slot, rules, zone))) {
                chosen.add(slot);
            }
        }
        for (TimeSlot slot : chronologicalSlots) {
            if (chosen.size() >= max) {
                break;
            }
            chosen.add(slot);
        }
        List<TimeSlot> ordered = new ArrayList<>(chosen);
        ordered.sort(Comparator.comparing(TimeSlot::getStart));
        return List.copyOf(ordered);
    }

    private static LocalTime windowStart(TimeSlot slot, SchedulingRules rules, ZoneId zone) {
        LocalTime start = slot.getStart().atZone(zone).toLocalTime();
        for (TimeWindow window : rules.getAvailableHours()) {
            if (!start.isBefore(window.getStart()) && start.isBefore(window.getEnd())) {
                return window.getStart();
            }
        }
        return start;
    }

    private static void addWindowSlots(SchedulingRules rules, ZoneId zone, LocalDate day,
            TimeWindow hours, 
            SearchWindow window, List<CalendarEvent> occupying, List<TimeSlot> slots) {
        int duration = rules.getMeetingDurationMinutes();
        LocalDateTime cursor = LocalDateTime.of(day, hours.getStart());
        LocalDateTime windowEnd = LocalDateTime.of(day, hours.getEnd());
        while (!cursor.plusMinutes(duration).isAfter(windowEnd)) {
            Instant start = cursor.atZone(zone).toInstant();
            Instant end = cursor.plusMinutes(duration).atZone(zone).toInstant();
            if (!start.isBefore(window.getFrom()) && start.isBefore(window.getTo())
                    && !overlapsBusy(start, end, occupying)) {
                slots.add(new TimeSlot(start, end));
            }
            cursor = cursor.plusMinutes(duration);
        }
    }

    private static boolean overlapsBusy(Instant start, Instant end, List<CalendarEvent> occupying) {
        for (CalendarEvent event : occupying) {
            if (event.isAllDay()) {
                continue;
            }
            if (start.isBefore(event.getEnd()) && event.getStart().isBefore(end)) {
                return true;
            }
        }
        return false;
    }

    private static boolean allDayBlocks(List<CalendarEvent> occupying, LocalDate day, ZoneId zone) {
        for (CalendarEvent event : occupying) {
            if (!event.isAllDay()) {
                continue;
            }
            LocalDate startDay = event.getStart().atZone(zone).toLocalDate();
            LocalDate endDay = event.getEnd().atZone(zone).toLocalDate();
            if (!day.isBefore(startDay) && day.isBefore(endDay)) {
                return true;
            }
        }
        return false;
    }

    private static List<CalendarEvent> occupying(List<CalendarEvent> events,
            String excludeEventId) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        List<CalendarEvent> occupying = new ArrayList<>();
        for (CalendarEvent event : events) {
            if (event.isCancelled() || event.isShowAsFree()) {
                continue;
            }
            if (excludeEventId != null && excludeEventId.equals(event.getId())) {
                continue;
            }
            occupying.add(event);
        }
        return occupying;
    }
}
