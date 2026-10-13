/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */

package dev.dora.domain.model;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Provider-independent rules that decide when Dora may book, move or cancel a meeting.
 *
 * <p>The factory rejects an inconsistent set of rules. Callers (the configuration
 * adapter at startup) surface that failure and stop the application.
 *
 * @author Rodrigo Prestes Machado
 */
public final class SchedulingRules {

    private final int meetingDurationMinutes;
    private final Set<DayOfWeek> availableWeekdays;
    private final List<TimeWindow> availableHours;
    private final ZoneId timezone;
    private final int searchWindowDays;
    private final boolean presentOptionsToUser;
    private final int maxOptionsToPresent;
    private final int minNoticeHours;
    private final Optional<Integer> minChangeNoticeHours;
    private final boolean notifyAttendees;

    private SchedulingRules(int meetingDurationMinutes, Set<DayOfWeek> availableWeekdays,
            List<TimeWindow> availableHours, 
            ZoneId timezone, int searchWindowDays, boolean presentOptionsToUser,
                    int maxOptionsToPresent, 
            int minNoticeHours, Optional<Integer> minChangeNoticeHours, boolean notifyAttendees) {
        this.meetingDurationMinutes = meetingDurationMinutes;
        this.availableWeekdays = Set.copyOf(availableWeekdays);
        this.availableHours = List.copyOf(availableHours);
        this.timezone = timezone;
        this.searchWindowDays = searchWindowDays;
        this.presentOptionsToUser = presentOptionsToUser;
        this.maxOptionsToPresent = maxOptionsToPresent;
        this.minNoticeHours = minNoticeHours;
        this.minChangeNoticeHours = minChangeNoticeHours;
        this.notifyAttendees = notifyAttendees;
    }

    /**
     * Validates and creates the rules.
     *
     * @param meetingDurationMinutes duration of every meeting, in minutes
     * @param availableWeekdays      weekdays on which a meeting may be booked
     * @param availableHours         local time windows that can hold a meeting
     * @param timezone               zone used to interpret days, windows and displayed times
     * @param searchWindowDays       how many local days ahead, counting today, may be searched
     * @param presentOptionsToUser   whether several options are offered instead of the earliest
     *     slot
     * @param maxOptionsToPresent    how many options to offer when the user chooses
     * @param minNoticeHours         minimum hours between now and a new meeting
     * @param minChangeNoticeHours   minimum hours before the current start to move or cancel;
     *                               empty means no extra restriction
     * @param notifyAttendees        whether the provider should notify attendees
     * @return the validated rules
     */
    public static SchedulingRules of(int meetingDurationMinutes, List<DayOfWeek> availableWeekdays,
            List<TimeWindow> availableHours, ZoneId timezone, int searchWindowDays,
                    boolean presentOptionsToUser, 
            int maxOptionsToPresent, int minNoticeHours, Optional<Integer> minChangeNoticeHours,
            boolean notifyAttendees) {
        if (meetingDurationMinutes <= 0) {
            throw new IllegalArgumentException(
                    "Configuração inválida: scheduling.meeting-duration-minutes deve ser maior "
                    + "que zero.");
        }
        if (availableWeekdays == null || availableWeekdays.isEmpty()
                || containsNull(availableWeekdays)) {
            throw new IllegalArgumentException(
                    "Configuração inválida: scheduling.available-weekdays deve ter ao menos um "
                    + "dia.");
        }
        if (availableHours == null || availableHours.isEmpty() || containsNull(availableHours)) {
            throw new IllegalArgumentException(
                    "Configuração inválida: scheduling.available-hours deve ter ao menos uma "
                    + "janela HH:mm-HH:mm.");
        }
        if (timezone == null) {
            throw new IllegalArgumentException(
                    "Configuração inválida: scheduling.timezone deve ser um fuso IANA, por "
                    + "exemplo America/Sao_Paulo.");
        }
        if (searchWindowDays <= 0) {
            throw new IllegalArgumentException(
                    "Configuração inválida: scheduling.search-window-days deve ser maior que "
                    + "zero.");
        }
        if (maxOptionsToPresent <= 0) {
            throw new IllegalArgumentException(
                    "Configuração inválida: scheduling.max-options-to-present deve ser maior "
                    + "que zero.");
        }
        if (minNoticeHours < 0) {
            throw new IllegalArgumentException(
                    "Configuração inválida: scheduling.min-notice-hours não pode ser negativo.");
        }
        Optional<Integer> changeNotice = minChangeNoticeHours == null
                ? Optional.empty() : minChangeNoticeHours;
        if (changeNotice.isPresent() && changeNotice.get() < 0) {
            throw new IllegalArgumentException(
                    "Configuração inválida: scheduling.min-change-notice-hours não pode ser "
                    + "negativo.");
        }

        List<TimeWindow> ordered = new ArrayList<>(availableHours);
        ordered.sort(Comparator.comparing(TimeWindow::getStart));
        for (int i = 1; i < ordered.size(); i++) {
            TimeWindow previous = ordered.get(i - 1);
            TimeWindow current = ordered.get(i);
            if (current.getStart().isBefore(previous.getEnd())) {
                throw new IllegalArgumentException(
                        "Configuração inválida: scheduling.available-hours tem janelas "
                        + "sobrepostas.");
            }
        }
        boolean fits = ordered.stream()
                .anyMatch(window -> Duration.between(window.getStart(),
                        window.getEnd()).toMinutes() >= meetingDurationMinutes);
        if (!fits) {
            throw new IllegalArgumentException(
                    "Configuração inválida: nenhuma janela de scheduling.available-hours cabe "
                    + "a duração de "
                            + meetingDurationMinutes + " minutos.");
        }

        return new SchedulingRules(meetingDurationMinutes, EnumSet.copyOf(availableWeekdays),
                ordered, timezone, 
                searchWindowDays, presentOptionsToUser, maxOptionsToPresent, minNoticeHours,
                        changeNotice, 
                notifyAttendees);
    }

    private static boolean containsNull(List<?> values) {
        for (Object value : values) {
            if (value == null) {
                return true;
            }
        }
        return false;
    }

    public int getMeetingDurationMinutes() {
        return meetingDurationMinutes;
    }

    public Set<DayOfWeek> getAvailableWeekdays() {
        return availableWeekdays;
    }

    public List<TimeWindow> getAvailableHours() {
        return availableHours;
    }

    public ZoneId getTimezone() {
        return timezone;
    }

    public int getSearchWindowDays() {
        return searchWindowDays;
    }

    public boolean isPresentOptionsToUser() {
        return presentOptionsToUser;
    }

    public int getMaxOptionsToPresent() {
        return maxOptionsToPresent;
    }

    public int getMinNoticeHours() {
        return minNoticeHours;
    }

    public Optional<Integer> getMinChangeNoticeHours() {
        return minChangeNoticeHours;
    }

    public boolean isNotifyAttendees() {
        return notifyAttendees;
    }
}
