/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */

package dev.dora.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A free meeting interval stored in UTC. Display converts it to the configured time zone.
 *
 * @author Rodrigo Prestes Machado
 */
public final class TimeSlot {

    /** Inclusive start, in UTC. */
    private final Instant start;

    /** Exclusive end, in UTC. */
    private final Instant end;

    /**
     * Creates a slot. The start must be strictly before the end.
     *
     * @param start inclusive UTC start
     * @param end   exclusive UTC end
     */
    public TimeSlot(Instant start, Instant end) {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new IllegalArgumentException("Um horário livre precisa ter início antes do fim.");
        }
        this.start = start;
        this.end = end;
    }

    public Instant getStart() {
        return start;
    }

    public Instant getEnd() {
        return end;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TimeSlot slot)) {
            return false;
        }
        return start.equals(slot.start) && end.equals(slot.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }
}
