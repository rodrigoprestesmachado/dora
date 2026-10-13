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
 * Cancels a meeting Dora created, after the user has confirmed.
 *
 * <p>Never throws. Without confirmation the meeting is left untouched and the message
 * asks the assistant to confirm the title and the time with the user. Repeating a
 * cancellation of an event that is already gone does not fail with a technical error.
 *
 * @author Rodrigo Prestes Machado
 */
public interface CancelMeetingUseCase {

    /**
     * Cancels the event when {@code confirmedByUser} is true and the rules allow it.
     *
     * @param eventId         id returned when the meeting was found or booked
     * @param reason          optional reason, included in the confirmation; blank is allowed
     * @param confirmedByUser {@code true} only after the user explicitly agreed, having been told
     *                        the title and the time
     * @return confirmation of what was cancelled, or a clear explanation when it was not
     */
    String cancel(String eventId, String reason, boolean confirmedByUser);
}
