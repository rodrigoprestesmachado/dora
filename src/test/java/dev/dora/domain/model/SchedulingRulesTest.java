package dev.dora.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SchedulingRules} validation.
 *
 * @author Rodrigo Prestes Machado
 */
class SchedulingRulesTest {

    @Test
    void rejectsNonPositiveDuration() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> rules(0));
        assertTrue(error.getMessage().contains("scheduling.meeting-duration-minutes"));
    }

    @Test
    void rejectsEmptyWeekdays() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> SchedulingRules.of(60, List.of(),
                        List.of(new TimeWindow(LocalTime.of(9, 0), LocalTime.of(12, 0))),
                        ZoneId.of("America/Sao_Paulo"), 30, true, 3, 24, Optional.of(2), true));
        assertTrue(error.getMessage().contains("scheduling.available-weekdays"));
    }

    @Test
    void rejectsWindowShorterThanTheMeeting() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> SchedulingRules.of(60, List.of(DayOfWeek.MONDAY),
                        List.of(new TimeWindow(LocalTime.of(9, 0), LocalTime.of(9, 30))),
                        ZoneId.of("America/Sao_Paulo"), 30, true, 3, 24, Optional.empty(), true));
        assertTrue(error.getMessage().contains("scheduling.available-hours"));
    }

    @Test
    void rejectsOverlappingWindows() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> SchedulingRules.of(30, List.of(DayOfWeek.MONDAY),
                        List.of(new TimeWindow(LocalTime.of(9, 0), LocalTime.of(12, 0)),
                                new TimeWindow(LocalTime.of(11, 0), LocalTime.of(13, 0))),
                        ZoneId.of("America/Sao_Paulo"), 30, true, 3, 24, Optional.empty(), true));
        assertTrue(error.getMessage().contains("sobrepostas"));
    }

    @Test
    void rejectsNonPositiveSearchWindow() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> SchedulingRules.of(60, List.of(DayOfWeek.MONDAY),
                        List.of(new TimeWindow(LocalTime.of(9, 0), LocalTime.of(12, 0))),
                        ZoneId.of("America/Sao_Paulo"), 0, true, 3, 24, Optional.empty(), true));
        assertTrue(error.getMessage().contains("scheduling.search-window-days"));
    }

    @Test
    void acceptsMissingChangeNotice() {
        SchedulingRules rules = SchedulingRules.of(30, List.of(DayOfWeek.MONDAY),
                List.of(new TimeWindow(LocalTime.of(9, 0), LocalTime.of(12, 0))),
                ZoneId.of("America/Sao_Paulo"), 30, false, 1, 0, Optional.empty(), false);
        assertEquals(30, rules.getMeetingDurationMinutes());
        assertTrue(rules.getMinChangeNoticeHours().isEmpty());
    }

    private static SchedulingRules rules(int duration) {
        return SchedulingRules.of(duration, List.of(DayOfWeek.MONDAY),
                List.of(new TimeWindow(LocalTime.of(9, 0), LocalTime.of(12, 0))),
                ZoneId.of("America/Sao_Paulo"), 30, true, 3, 24, Optional.of(2), true);
    }
}
