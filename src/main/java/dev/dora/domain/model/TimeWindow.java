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

import java.time.LocalTime;

/**
 * A daily interval, in local time, during which a meeting may start and end.
 *
 * @author Rodrigo Prestes Machado
 */
public final class TimeWindow {

    /** Inclusive start of the window. */
    private final LocalTime start;

    /** Exclusive end of the window: a meeting must finish at or before this time. */
    private final LocalTime end;

    /**
     * Creates a window. The start must be strictly before the end (no overnight windows).
     *
     * @param start inclusive local start
     * @param end   exclusive local end
     */
    public TimeWindow(LocalTime start, LocalTime end) {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new IllegalArgumentException(
                    "scheduling.available-hours possui uma janela inválida. Use intervalos "
                    + "HH:mm-HH:mm com início antes do fim.");
        }
        this.start = start;
        this.end = end;
    }

    public LocalTime getStart() {
        return start;
    }

    public LocalTime getEnd() {
        return end;
    }
}
