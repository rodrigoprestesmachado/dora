/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */

package dev.dora.domain.scheduling;

import java.time.Instant;

/**
 * The UTC interval inside which a proposed meeting may start.
 *
 * @author Rodrigo Prestes Machado
 */
public final class SearchWindow {

    /** Earliest allowed start, inclusive. */
    private final Instant from;

    /** Latest allowed start, exclusive. */
    private final Instant to;

    /**
     * Creates a window. {@code from} may be equal to or after {@code to} when nothing can be
     * offered.
     *
     * @param from inclusive earliest start
     * @param to   exclusive latest start
     */
    public SearchWindow(Instant from, Instant to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("A janela de busca precisa de início e fim.");
        }
        this.from = from;
        this.to = to;
    }

    public Instant getFrom() {
        return from;
    }

    public Instant getTo() {
        return to;
    }

    /**
     * Tells whether this window still has room for a start instant.
     *
     * @return {@code true} when at least one instant could still be a start
     */
    public boolean isEmpty() {
        return !from.isBefore(to);
    }
}
