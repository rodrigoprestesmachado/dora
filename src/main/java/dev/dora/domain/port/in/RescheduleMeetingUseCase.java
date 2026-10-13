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
 * Moves a meeting Dora created onto a new valid slot, keeping the same event and conference link.
 *
 * <p>Never throws. The event's current time is not treated as a conflict. Moving it onto the
 * same start does not call the provider. A failed update leaves the original event as it was.
 *
 * @author Rodrigo Prestes Machado
 */
public interface RescheduleMeetingUseCase {

    /**
     * Moves the event to the chosen start. The duration stays the configured one.
     *
     * @param eventId          id returned when the meeting was found or booked
     * @param newStartDateTime new local start ({@code yyyy-MM-dd'T'HH:mm}) in the configured time
     *     zone
     * @return the previous time, the new time, the time zone, the same conference link and
     *     the same id, or a clear explanation when nothing changed
     */
    String reschedule(String eventId, String newStartDateTime);
}
