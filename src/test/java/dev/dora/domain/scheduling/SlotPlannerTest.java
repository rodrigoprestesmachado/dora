package dev.dora.domain.scheduling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dora.application.SchedulingFixtures;
import dev.dora.domain.model.CalendarEvent;
import dev.dora.domain.model.SchedulingRules;
import dev.dora.domain.model.TimeSlot;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for free-slot calculation. No Quarkus and no calendar provider.
 *
 * @author Rodrigo Prestes Machado
 */
class SlotPlannerTest {

    private final SlotPlanner planner = new SlotPlanner();
    private final Clock clock = Clock.fixed(SchedulingFixtures.NOW, SchedulingFixtures.ZONE);

    @Test
    void offersOnlyAllowedWeekdaysAndHours() {
        List<TimeSlot> slots = planner.availableSlots(SchedulingFixtures.rules(), clock, List.of(),
                null, null, null);

        assertTrue(startsAt(slots, "2026-10-13T09:00"));
        assertTrue(startsAt(slots, "2026-10-13T11:00"));
        assertTrue(startsAt(slots, "2026-10-13T14:00"));
        assertFalse(startsAt(slots, "2026-10-13T12:00"));
        assertFalse(startsAt(slots, "2026-10-13T13:00"));
        assertFalse(startsAt(slots, "2026-10-12T09:00"));
        assertFalse(startsAt(slots, "2026-10-16T09:00"));
    }

    @Test
    void durationChangesTheGrid() {
        SchedulingRules halfHour = SchedulingFixtures.rules(30, true, 3, 24, Optional.of(2), true,
                30);

        List<TimeSlot> slots = planner.availableSlots(halfHour, clock, List.of(), null, null, null);

        assertTrue(startsAt(slots, "2026-10-13T09:00"));
        assertTrue(startsAt(slots, "2026-10-13T09:30"));
        assertFalse(startsAt(slots, "2026-10-13T09:15"));
    }

    @Test
    void dropsSlotsThatOverlapABusyEvent() {
        CalendarEvent busy = event("busy", "2026-10-13T09:00", "2026-10-13T10:00", false, false,
                false, false);

        List<TimeSlot> slots = planner.availableSlots(SchedulingFixtures.rules(), clock,
                List.of(busy), null, null, null);

        assertFalse(startsAt(slots, "2026-10-13T09:00"));
        assertTrue(startsAt(slots, "2026-10-13T10:00"));
    }

    @Test
    void tentativeEventOccupiesTheSlotAndAFreeEventDoesNot() {
        CalendarEvent tentative = event("maybe", "2026-10-13T10:00", "2026-10-13T11:00", false,
                true, false, false);
        CalendarEvent free = event("free", "2026-10-13T14:00", "2026-10-13T15:00", false, false,
                false, true);

        List<TimeSlot> slots = planner.availableSlots(SchedulingFixtures.rules(), clock,
                List.of(tentative, free), null, 
                null, null);

        assertFalse(startsAt(slots, "2026-10-13T10:00"));
        assertTrue(startsAt(slots, "2026-10-13T14:00"));
    }

    @Test
    void cancelledEventDoesNotOccupy() {
        CalendarEvent cancelled = event("gone", "2026-10-13T09:00", "2026-10-13T10:00", false,
                false, true, false);

        List<TimeSlot> slots = planner.availableSlots(SchedulingFixtures.rules(), clock,
                List.of(cancelled), null, null, 
                null);

        assertTrue(startsAt(slots, "2026-10-13T09:00"));
    }

    @Test
    void allDayEventBlocksTheLocalDay() {
        CalendarEvent allDay = new CalendarEvent("holiday", "Feriado",
                SchedulingFixtures.at("2026-10-13T00:00"), 
                SchedulingFixtures.at("2026-10-14T00:00"), true, List.of(), null, false, false,
                        false, false);

        List<TimeSlot> slots = planner.availableSlots(SchedulingFixtures.rules(), clock,
                List.of(allDay), null, null, 
                null);

        assertFalse(startsAt(slots, "2026-10-13T09:00"));
        assertFalse(startsAt(slots, "2026-10-13T14:00"));
        assertTrue(startsAt(slots, "2026-10-14T09:00"));
    }

    @Test
    void ignoredEventDoesNotCountAsAConflict() {
        CalendarEvent own = event("mine", "2026-10-13T09:00", "2026-10-13T10:00", true, false,
                false, false);

        List<TimeSlot> slots = planner.availableSlots(SchedulingFixtures.rules(), clock,
                List.of(own), null, null, 
                "mine");

        assertTrue(startsAt(slots, "2026-10-13T09:00"));
    }

    @Test
    void nothingStartsOnOrAfterTheSearchHorizon() {
        SchedulingRules oneDay = SchedulingFixtures.rules(60, true, 3, 0, Optional.empty(), true,
                1);
        List<TimeSlot> slots = planner.availableSlots(oneDay, clock, List.of(), null, null, null);

        assertTrue(startsAt(slots, "2026-10-12T09:00"));
        assertFalse(startsAt(slots, "2026-10-13T09:00"));
    }

    @Test
    void thirtyDayWindowIncludesTheLastAllowedDayOnly() {
        List<TimeSlot> slots = planner.availableSlots(SchedulingFixtures.rules(), clock, List.of(),
                null, null, null);

        assertTrue(startsAt(slots, "2026-11-10T09:00"));
        assertFalse(startsAt(slots, "2026-11-11T09:00"));
    }

    @Test
    void presentSpreadsOptionsAcrossDaysAndCanReturnOnlyTheEarliest() {
        List<TimeSlot> slots = planner.availableSlots(SchedulingFixtures.rules(), clock, List.of(),
                null, null, null);

        List<TimeSlot> options = planner.present(slots, SchedulingFixtures.rules());
        assertEquals(3, options.size());
        assertEquals(SchedulingFixtures.at("2026-10-13T09:00"), options.get(0).getStart());
        assertEquals(SchedulingFixtures.at("2026-10-13T10:00"), options.get(1).getStart());
        assertEquals(SchedulingFixtures.at("2026-10-13T14:00"), options.get(2).getStart());

        SchedulingRules automatic = SchedulingFixtures.rules(60, false, 3, 24, Optional.of(2), true,
                30);
        List<TimeSlot> onlyOne = planner.present(slots, automatic);
        assertEquals(List.of(slots.get(0)), onlyOne);
    }

    @Test
    void presentFillsTheSameDayWhenThereAreNotEnoughDays() {
        SchedulingRules tuesdayOnly = SchedulingFixtures.rules(60, true, 3, 24, Optional.of(2),
                true, 30);
        Instant from = SchedulingFixtures.at("2026-10-13T00:00");
        Instant to = SchedulingFixtures.at("2026-10-14T00:00");

        List<TimeSlot> options = planner.present(
                planner.availableSlots(tuesdayOnly, clock, List.of(), from, to, null), tuesdayOnly);

        assertEquals(3, options.size());
        assertEquals(SchedulingFixtures.at("2026-10-13T09:00"), options.get(0).getStart());
        assertEquals(SchedulingFixtures.at("2026-10-13T10:00"), options.get(1).getStart());
        assertEquals(SchedulingFixtures.at("2026-10-13T14:00"), options.get(2).getStart());
    }

    private static boolean startsAt(List<TimeSlot> slots, String localDateTime) {
        Instant start = SchedulingFixtures.at(localDateTime);
        return slots.stream().anyMatch(slot -> slot.getStart().equals(start));
    }

    private static CalendarEvent event(String id, String start, String end, boolean createdByDora,
            boolean tentative, 
            boolean cancelled, boolean showAsFree) {
        return new CalendarEvent(id, id, SchedulingFixtures.at(start), SchedulingFixtures.at(end),
                false, List.of(), 
                null, createdByDora, cancelled, tentative, showAsFree);
    }
}
