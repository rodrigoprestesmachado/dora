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
 * Thrown when an update or cancellation targets an event the provider no longer has.
 *
 * <p>Repeating the call must not create another event. Callers translate this into a
 * clear "already cancelled or unknown" message.
 *
 * @author Rodrigo Prestes Machado
 */
public class CalendarEventNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception with a message describing the missing event.
     *
     * @param message a human-readable description of the missing event
     */
    public CalendarEventNotFoundException(String message) {
        super(message);
    }
}
