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
 * Thrown by a {@link ProcessLookupPort} implementation when a process lookup
 * could not be completed due to a technical failure (site unavailable, layout
 * change, automation timeout, suspected anti-bot block, etc.), as opposed to a
 * legitimate "process not found" outcome.
 *
 * @author Rodrigo Prestes Machado
 */
public class ProcessLookupException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception with a message only.
     *
     * @param message a human-readable description of the failure
     */
    public ProcessLookupException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a message and the underlying cause.
     *
     * @param message a human-readable description of the failure
     * @param cause   the underlying failure encountered while looking up the process
     */
    public ProcessLookupException(String message, Throwable cause) {
        super(message, cause);
    }
}
