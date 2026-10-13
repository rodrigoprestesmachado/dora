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
 * Chooses which free slots Dora should show before booking or moving a meeting.
 *
 * <p>Never throws. When the rules say the user does not choose, the result is the single
 * earliest slot and tells the assistant to book it. Otherwise it offers a few slots,
 * preferably on different days, and tells the assistant to wait.
 *
 * @author Rodrigo Prestes Machado
 */
public interface SuggestMeetingSlotsUseCase {

    /**
     * Suggests slots inside the optional local-date range.
     *
     * @param fromDate       inclusive start date ({@code yyyy-MM-dd}), or blank for today
     * @param toDate         inclusive end date ({@code yyyy-MM-dd}), or blank for the
     *     configured horizon
     * @param excludeEventId event to ignore as a conflict, used when moving that event; blank
     *     for a new meeting
     * @return the options the assistant should present, or a clear explanation when there are none
     */
    String suggest(String fromDate, String toDate, String excludeEventId);
}
