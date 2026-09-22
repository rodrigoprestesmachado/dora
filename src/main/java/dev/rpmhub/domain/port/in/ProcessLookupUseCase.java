/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */
package dev.rpmhub.domain.port.in;

/**
 * Driving port for looking up the status of a legal process ("processo") on
 * behalf of the Dora agent.
 *
 * @author Rodrigo Prestes Machado
 */
public interface ProcessLookupUseCase {

    /**
     * Looks up a legal process by number and returns a human/LLM-readable result.
     *
     * <p>Never throws: validation failures, "not found" outcomes and technical
     * errors are all translated into a clear natural-language message so the
     * agent can relay it to the client instead of failing silently.
     *
     * @param rawProcessNumber the process number as provided by the client, in any
     *                          common CNJ formatting (with or without punctuation)
     * @return Markdown describing the process status/movements on success, or a
     *         clear natural-language explanation when the number is invalid, the
     *         process was not found, or the lookup failed
     */
    String lookup(String rawProcessNumber);
}
