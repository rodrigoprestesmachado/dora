/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */

package dev.dora.adapter.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

/**
 * Typed {@code scheduling.*} configuration. Defaults live here; profiles and environment
 * variables override them. Secrets never belong in this mapping.
 *
 * @author Rodrigo Prestes Machado
 */
@ConfigMapping(prefix = "scheduling")
public interface SchedulingConfig {

    /**
     * Active calendar provider. A new provider is selected by matching this value,
     * without changing tools or scheduling rules.
     *
     * @return provider key, for example {@code google}
     */
    @WithDefault("google")
    String calendarProvider();

    /**
     * Default meeting length.
     *
     * @return meeting length in minutes
     */
    @WithDefault("60")
    int meetingDurationMinutes();

    /**
     * Days of the week on which meetings may be booked.
     *
     * @return weekdays on which a meeting may be booked
     */
    @WithDefault("MONDAY,TUESDAY,WEDNESDAY,THURSDAY")
    List<DayOfWeek> availableWeekdays();

    /**
     * Comma-separated local windows, each {@code HH:mm-HH:mm}.
     *
     * @return the raw window list
     */
    @WithDefault("09:00-12:00,14:00-18:00")
    String availableHours();

    /**
     * Time zone used to interpret and display scheduling data.
     *
     * @return IANA time zone used to interpret days, windows and displayed times
     */
    @WithDefault("America/Sao_Paulo")
    String timezone();

    /**
     * How far ahead free slots may be searched.
     *
     * @return how many local days ahead, counting today, may be searched
     */
    @WithDefault("30")
    int searchWindowDays();

    /**
     * Controls whether Dora offers options or books the earliest slot directly.
     *
     * @return {@code true} when Dora must offer options and wait; {@code false} to take the
     *     earliest slot
     */
    @WithDefault("true")
    boolean presentOptionsToUser();

    /**
     * Maximum number of options shown to the user at once.
     *
     * @return how many options to offer when the user chooses
     */
    @WithDefault("3")
    int maxOptionsToPresent();

    /**
     * Minimum lead time required to create a new meeting.
     *
     * @return minimum hours between now and a new meeting
     */
    @WithDefault("24")
    int minNoticeHours();

    /**
     * Minimum hours before the current start to move or cancel a meeting.
     * Absent means there is no extra restriction.
     *
     * @return the notice, or empty
     */
    Optional<Integer> minChangeNoticeHours();

    /**
     * Controls attendee notifications sent by the calendar provider.
     *
     * @return whether the provider should notify attendees on create, move and cancel
     */
    @WithDefault("true")
    boolean notifyAttendees();

    /**
     * Office address told to the client and stored on in-person events.
     *
     * @return the address, for example the Pelotas office
     */
    @WithDefault("R. Uruguai, 902 - Centro, Pelotas - RS, 96010-630")
    String officeAddress();
}
