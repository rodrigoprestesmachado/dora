/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */

package dev.dora.application;

import dev.dora.domain.model.CalendarEvent;
import dev.dora.domain.model.SchedulingRules;
import dev.dora.domain.port.in.CancelMeetingUseCase;
import dev.dora.domain.port.out.CalendarAccessException;
import dev.dora.domain.port.out.CalendarEventNotFoundException;
import dev.dora.domain.port.out.CalendarPort;
import java.time.Clock;
import java.util.logging.Logger;

/**
 * Cancels a Dora meeting after the user confirms, or explains why it cannot be cancelled.
 *
 * <p>A failed cancellation does not change the event. Plain Java: wiring lives in
 * {@code dev.dora.adapter.config.ApplicationBeans}.
 *
 * @author Rodrigo Prestes Machado
 */
public class CancelMeetingService implements CancelMeetingUseCase {

    private static final Logger LOG = Logger.getLogger(CancelMeetingService.class.getName());

    private final CalendarPort calendarPort;
    private final SchedulingRules rules;
    private final Clock clock;

    /**
     * Creates the use case.
     *
     * @param calendarPort calendar provider
     * @param rules        scheduling rules
     * @param clock        source of the current instant
     */
    public CancelMeetingService(CalendarPort calendarPort, SchedulingRules rules, Clock clock) {
        this.calendarPort = calendarPort;
        this.rules = rules;
        this.clock = clock;
    }

    @Override
    public String cancel(String eventId, String reason, boolean confirmedByUser) {
        try {
            String id = SchedulingTexts.requireEventId(eventId);
            CalendarEvent current = calendarPort.findEvent(id).orElse(null);
            if (current == null) {
                return "Não encontrei essa reunião. Ela pode já ter sido cancelada.";
            }
            String refusal = SchedulingTexts.refusalToChange(current, rules, clock, "cancelar");
            if (refusal != null) {
                return refusal;
            }
            if (!confirmedByUser) {
                return "Antes de cancelar, confirme com o cliente, citando o título e o "
                        + "horário.\nTítulo: "
                        + current.getTitle() + "\nData e hora: "
                        + SchedulingTexts.formatInterval(current.getStart(), current.getEnd(),
                                rules.getTimezone())
                        + "\nID: " + current.getId()
                        + "\nSó chame o cancelamento de novo depois que o cliente confirmar.";
            }
            calendarPort.cancelEvent(id, rules.isNotifyAttendees());
            String confirmation = "Reunião cancelada.\nTítulo: " + current.getTitle()
                    + "\nData e hora: "
                    + SchedulingTexts.formatInterval(current.getStart(), current.getEnd(),
                            rules.getTimezone())
                    + "\nID: " + current.getId()
                    + "\nInforme ao cliente qual reunião foi cancelada.";
            if (reason != null && !reason.isBlank()) {
                confirmation += "\nMotivo: " + reason.trim();
            }
            return confirmation;
        } catch (IllegalArgumentException exception) {
            return exception.getMessage();
        } catch (CalendarEventNotFoundException exception) {
            return "Não encontrei essa reunião. Ela pode já ter sido cancelada.";
        } catch (CalendarAccessException exception) {
            return SchedulingTexts.unavailable(LOG, exception);
        } catch (RuntimeException exception) {
            return SchedulingTexts.unexpected(LOG, exception);
        }
    }
}
