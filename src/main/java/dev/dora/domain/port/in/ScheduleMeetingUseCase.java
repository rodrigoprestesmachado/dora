/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */

package dev.dora.domain.port.in;

/**
 * Creates a meeting on the calendar after checking the slot is still free.
 *
 * <p>Never throws. Repeating the same title, start and attendees returns the meeting
 * already created instead of a second event.
 *
 * @author Rodrigo Prestes Machado
 */
public interface ScheduleMeetingUseCase {

    /**
     * Books the chosen start time for the configured duration.
     *
     * @param startDateTime  local start ({@code yyyy-MM-dd'T'HH:mm}) in the configured time zone,
     *                       or an instant with an offset
     * @param title          meeting title, the name of the person who asked
     * @param attendeeEmails one or more e-mail addresses, separated by comma, semicolon or space;
     *                       required for online and in-person meetings
     * @param mode           {@code online} or {@code presencial}
     * @param notes          subject the client chose to share; blank when they declined
     * @return confirmation with the date, time and time zone, plus the conference link or the
     *     office address, or a clear explanation when the meeting was not created
     */
    String schedule(String startDateTime, String title, String attendeeEmails, String mode,
            String notes);
}
