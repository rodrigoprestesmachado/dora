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
 * Thrown when the provider refuses to create or move an event because the time is taken.
 *
 * <p>The original event is left unchanged. Callers should offer other slots.
 *
 * @author Rodrigo Prestes Machado
 */
public class CalendarConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception with a message describing the conflicting slot.
     *
     * @param message a human-readable description of the conflicting slot
     */
    public CalendarConflictException(String message) {
        super(message);
    }
}
