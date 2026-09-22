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

import java.util.Optional;

import dev.rpmhub.domain.model.ProcessLookupResult;

/**
 * Driven port (out) for looking up the status of a legal process ("processo")
 * on a court's public search system (e.g. TJRS).
 *
 * @author Rodrigo Prestes Machado
 */
public interface ProcessLookupPort {

    /**
     * Looks up a legal process by its canonical CNJ number.
     *
     * @param cnjNumber the process number, already normalized to the canonical
     *                   CNJ format ({@code NNNNNNN-DD.AAAA.J.TR.OOOO})
     * @return Optional containing the scraped result, or empty if the process was
     *         not found (e.g. non-existent number, or under judicial secrecy)
     * @throws ProcessLookupException if the lookup could not be completed due to a
     *                                 site/network/automation error (as opposed to a
     *                                 legitimate "not found" outcome)
     */
    Optional<ProcessLookupResult> lookup(String cnjNumber);
}
