/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */
package dev.rpmhub.domain.port.out;

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

    public ProcessLookupException(String message) {
        super(message);
    }

    public ProcessLookupException(String message, Throwable cause) {
        super(message, cause);
    }
}
