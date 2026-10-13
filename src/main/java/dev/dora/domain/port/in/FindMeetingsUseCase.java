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
 * Finds future meetings created by Dora so the user can move or cancel one without knowing its id.
 *
 * <p>Never throws. When more than one meeting matches,
 * the message asks the assistant to let the user choose.
 *
 * @author Rodrigo Prestes Machado
 */
public interface FindMeetingsUseCase {

    /**
     * Lists future meetings created by Dora.
     *
     * @param attendeeEmail optional attendee address; blank matches any
     * @param fromDate      optional inclusive start date ({@code yyyy-MM-dd})
     * @param toDate        optional inclusive end date ({@code yyyy-MM-dd})
     * @param titleQuery    optional case-insensitive fragment of the title
     * @return the matching meetings, or a clear explanation when there are none
     */
    String find(String attendeeEmail, String fromDate, String toDate, String titleQuery);
}
