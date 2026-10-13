package dev.dora.adapter.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for turning {@link SchedulingConfig} into rules, without starting Quarkus.
 *
 * @author Rodrigo Prestes Machado
 */
class SchedulingRulesFactoryTest {

    @Test
    void mapsTheConfiguredValues() {
        var rules = SchedulingRulesFactory.create(config("60", "MONDAY,TUESDAY",
                "09:00-12:00,14:00-18:00", 
                "America/Sao_Paulo", 30, true, 3, 24, Optional.of(2), true));

        assertEquals(60, rules.getMeetingDurationMinutes());
        assertEquals(List.of(DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY), List.copyOf(rules.getAvailableWeekdays().stream()
                .sorted().toList()).stream().filter(day -> day == DayOfWeek.MONDAY
                        || day == DayOfWeek.TUESDAY).toList());
        assertEquals(2, rules.getAvailableHours().size());
        assertTrue(rules.isNotifyAttendees());
    }

    @Test
    void rejectsAMalformedWindow() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> SchedulingRulesFactory.create(config("60", "MONDAY", "09:00",
                        "America/Sao_Paulo", 30, true, 3, 
                        24, Optional.empty(), true)));
        assertTrue(error.getMessage().contains("scheduling.available-hours"));
    }

    @Test
    void rejectsAnUnknownTimeZone() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> SchedulingRulesFactory.create(config("30", "MONDAY", "09:00-12:00",
                        "Mars/Olympus", 30, true, 3, 
                        0, Optional.empty(), false)));
        assertTrue(error.getMessage().contains("scheduling.timezone"));
    }

    @Test
    void changingOnlyTheDurationIsEnoughToChangeTheRules() {
        var hour = SchedulingRulesFactory.create(config("60", "MONDAY", "09:00-12:00",
                "America/Sao_Paulo", 30, true, 3, 
                24, Optional.of(2), true));
        var half = SchedulingRulesFactory.create(config("30", "MONDAY", "09:00-12:00",
                "America/Sao_Paulo", 30, true, 3, 
                24, Optional.of(2), true));

        assertEquals(60, hour.getMeetingDurationMinutes());
        assertEquals(30, half.getMeetingDurationMinutes());
    }

    private static SchedulingConfig config(String duration, String weekdays, String hours,
            String zone, int searchDays, 
            boolean present, int maxOptions, int minNotice, Optional<Integer> minChange,
                    boolean notify) {
        return new SchedulingConfig() {
            @Override
            public String calendarProvider() {
                return "google";
            }

            @Override
            public int meetingDurationMinutes() {
                return Integer.parseInt(duration);
            }

            @Override
            public List<DayOfWeek> availableWeekdays() {
                return List.of(weekdays.split(",")).stream().map(DayOfWeek::valueOf).toList();
            }

            @Override
            public String availableHours() {
                return hours;
            }

            @Override
            public String timezone() {
                return zone;
            }

            @Override
            public int searchWindowDays() {
                return searchDays;
            }

            @Override
            public boolean presentOptionsToUser() {
                return present;
            }

            @Override
            public int maxOptionsToPresent() {
                return maxOptions;
            }

            @Override
            public int minNoticeHours() {
                return minNotice;
            }

            @Override
            public Optional<Integer> minChangeNoticeHours() {
                return minChange;
            }

            @Override
            public boolean notifyAttendees() {
                return notify;
            }

            @Override
            public String officeAddress() {
                return "R. Uruguai, 902 - Centro, Pelotas - RS, 96010-630";
            }
        };
    }
}
