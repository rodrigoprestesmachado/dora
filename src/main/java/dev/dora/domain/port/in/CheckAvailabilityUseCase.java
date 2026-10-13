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
 * Lists free meeting times inside the configured rules.
 *
 * <p>Never throws: invalid input and provider failures become a message the assistant can relay.
 *
 * @author Rodrigo Prestes Machado
 */
public interface CheckAvailabilityUseCase {

    /**
     * Lists free slots between the given local dates, clamped to the configured search window.
     *
     * @param fromDate inclusive start date ({@code yyyy-MM-dd}), or blank for today
     * @param toDate   inclusive end date ({@code yyyy-MM-dd}), or blank for the configured horizon
     * @return the free slots, or a clear explanation when there are none or the request is invalid
     */
    String check(String fromDate, String toDate);
}
