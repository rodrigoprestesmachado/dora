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

import dev.dora.domain.model.SchedulingRules;
import dev.dora.domain.model.TimeWindow;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Fixed clock and rules shared by the scheduling unit tests.
 * Monday 12 Oct 2026, 08:00 in America/Sao_Paulo.
 *
 * @author Rodrigo Prestes Machado
 */
public final class SchedulingFixtures {

    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    /** 2026-10-12T08:00-03:00. */
    public static final Instant NOW = LocalDateTime.of(2026, 10, 12, 8, 0).atZone(ZONE).toInstant();

    /** Address stored on in-person meetings in the unit tests. */
    public static final String OFFICE = "R. Uruguai, 902 - Centro, Pelotas - RS, 96010-630";

    private SchedulingFixtures() {
    }

    /**
     * Parses a local date-time fixture into an instant.
     *
     * @param localDateTime an ISO local date-time, e.g. {@code "2026-10-12T08:00"}
     * @return the equivalent instant in {@link #ZONE}
     */
    public static Instant at(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(ZONE).toInstant();
    }

    /**
     * Builds the default scheduling rules used by most tests.
     *
     * @return default scheduling rules used by most tests
     */
    public static SchedulingRules rules() {
        return rules(60, true, 3, 24, Optional.of(2), true, 30);
    }

    /**
     * Builds scheduling rules from the given parameters.
     *
     * @param durationMinutes      default meeting duration
     * @param presentOptions       whether multiple slot options should be offered
     * @param maxOptions           maximum number of options to present
     * @param minNoticeHours       minimum notice required to schedule a meeting
     * @param minChangeNoticeHours minimum notice required to reschedule or cancel
     * @param notify               whether the client should be notified of changes
     * @param searchWindowDays     how many days ahead to search for free slots
     * @return scheduling rules built from the given parameters
     */
    public static SchedulingRules rules(int durationMinutes, boolean presentOptions, int maxOptions,
            int minNoticeHours, 
            Optional<Integer> minChangeNoticeHours, boolean notify, int searchWindowDays) {
        return SchedulingRules.of(durationMinutes,
                List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY),
                List.of(new TimeWindow(LocalTime.of(9, 0), LocalTime.of(12, 0)),
                        new TimeWindow(LocalTime.of(14, 0), LocalTime.of(18, 0))),
                ZONE, searchWindowDays, presentOptions, maxOptions, minNoticeHours,
                        minChangeNoticeHours, notify);
    }
}
