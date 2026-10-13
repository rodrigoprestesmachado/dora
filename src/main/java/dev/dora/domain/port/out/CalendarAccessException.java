/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */

package dev.dora.domain.port.out;

/**
 * Thrown by a {@link CalendarPort} when a calendar operation could not be completed
 * because the provider was unreachable or rejected the call for a technical reason.
 *
 * <p>A missing event is not this exception: {@link CalendarPort#findEvent(String)}
 * returns an empty optional, and a delete of an event that is already gone is reported
 * with {@link CalendarEventNotFoundException}.
 *
 * @author Rodrigo Prestes Machado
 */
public class CalendarAccessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception with a message only.
     *
     * @param message a human-readable description of the failure
     */
    public CalendarAccessException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a message and the underlying cause.
     *
     * @param message a human-readable description of the failure
     * @param cause   the underlying failure reported by the calendar provider
     */
    public CalendarAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
