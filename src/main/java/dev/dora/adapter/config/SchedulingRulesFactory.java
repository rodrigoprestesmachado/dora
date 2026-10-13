/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */

package dev.dora.adapter.config;

import dev.dora.domain.model.SchedulingRules;
import dev.dora.domain.model.TimeWindow;
import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps {@link SchedulingConfig} into {@link SchedulingRules}. Invalid values throw
 * {@link IllegalArgumentException} with the property name, which aborts application startup.
 *
 * @author Rodrigo Prestes Machado
 */
public final class SchedulingRulesFactory {

    private SchedulingRulesFactory() {
    }

    /**
     * Converts and validates the configuration.
     *
     * @param config typed scheduling configuration
     * @return rules the application layer can use without Quarkus types
     */
    public static SchedulingRules create(SchedulingConfig config) {
        if (config.calendarProvider() == null || config.calendarProvider().isBlank()) {
            throw new IllegalArgumentException(
                    "Configuração inválida: scheduling.calendar-provider não pode ficar vazio.");
        }
        ZoneId zone;
        try {
            zone = ZoneId.of(config.timezone());
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException(
                    "Configuração inválida: scheduling.timezone deve ser um fuso IANA, por "
                    + "exemplo America/Sao_Paulo.");
        }
        return SchedulingRules.of(config.meetingDurationMinutes(), config.availableWeekdays(),
                parseHours(config.availableHours()), zone, config.searchWindowDays(),
                        config.presentOptionsToUser(), 
                config.maxOptionsToPresent(), config.minNoticeHours(),
                        config.minChangeNoticeHours(), 
                config.notifyAttendees());
    }

    /**
     * Parses {@code HH:mm-HH:mm} windows separated by commas.
     *
     * @param raw the {@code scheduling.available-hours} value
     * @return the windows, in the order they were written
     */
    static List<TimeWindow> parseHours(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(
                    "Configuração inválida: scheduling.available-hours deve ter ao menos uma "
                    + "janela HH:mm-HH:mm.");
        }
        List<TimeWindow> windows = new ArrayList<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] bounds = trimmed.split("-");
            if (bounds.length != 2) {
                throw invalidWindow(trimmed);
            }
            try {
                windows.add(new TimeWindow(LocalTime.parse(bounds[0].trim()),
                        LocalTime.parse(bounds[1].trim())));
            } catch (DateTimeParseException | IllegalArgumentException exception) {
                throw invalidWindow(trimmed);
            }
        }
        if (windows.isEmpty()) {
            throw new IllegalArgumentException(
                    "Configuração inválida: scheduling.available-hours deve ter ao menos uma "
                    + "janela HH:mm-HH:mm.");
        }
        return List.copyOf(windows);
    }

    private static IllegalArgumentException invalidWindow(String window) {
        return new IllegalArgumentException(
                "Configuração inválida: scheduling.available-hours tem a janela \""
                + window + "\". Use HH:mm-HH:mm com início antes do fim.");
    }
}
