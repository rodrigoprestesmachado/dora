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
import dev.dora.domain.model.MeetingMode;
import dev.dora.domain.model.SchedulingRules;
import dev.dora.domain.model.TimeSlot;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Portuguese text and parsing shared by the scheduling use cases.
 *
 * @author Rodrigo Prestes Machado
 */
final class SchedulingTexts {

    static final String NO_SLOTS =
            "Não há horários livres nesse período, dentro dos dias e horários de atendimento. "
            + "Ofereça outro período ainda dentro da janela de busca e chame a ferramenta de novo. "
            + "Não ofereça mudar os dias ou horários de atendimento do escritório.";

    static final String UNAVAILABLE =
            "Não consegui acessar a agenda agora. Tente novamente em alguns instantes.";

    static final String UNEXPECTED =
            "Ocorreu um erro inesperado ao acessar a agenda. Tente novamente mais tarde.";

    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern(
            "EEEE, dd/MM/yyyy 'às' HH:mm", Locale.of("pt", "BR"));

    private static final DateTimeFormatter CLOCK_TIME = DateTimeFormatter.ofPattern("HH:mm");

    private static final DateTimeFormatter MACHINE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private SchedulingTexts() {
    }

    /**
     * Tells the model the requested dates do not overlap the bookable window.
     *
     * @param rules    scheduling rules
     * @param clock    source of the current instant
     * @param fromDate requested start, as sent by the model; may be blank
     * @param toDate   requested end, as sent by the model; may be blank
     * @return instruction to search again inside the current window
     */
    static String outsideWindow(SchedulingRules rules, Clock clock, String fromDate,
            String toDate) {
        ZoneId zone = rules.getTimezone();
        ZonedDateTime now = clock.instant().atZone(zone);
        LocalDate firstDay = now.plusHours(rules.getMinNoticeHours()).toLocalDate();
        LocalDate lastDay = now.toLocalDate().plusDays(rules.getSearchWindowDays()).minusDays(1);
        DateTimeFormatter day = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String today = now.format(DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy",
                Locale.of("pt", "BR")));
        StringBuilder text = new StringBuilder();
        String from = blankToNull(fromDate);
        String to = blankToNull(toDate);
        if (from != null) {
            text.append("fromDate ").append(from);
            if (to != null) {
                text.append(" e toDate ").append(to);
            }
            text.append(" estão fora da janela de busca. ");
        }
        text.append("Hoje é ").append(today).append(". A agenda aceita inícios de ")
                .append(firstDay.format(day)).append(" até ").append(lastDay.format(day))
                .append(". Chame de novo com fromDate e toDate dentro desse intervalo, ")
                .append("neste ano. Não diga ao cliente que não há horários e não ofereça ")
                .append("mudar os dias ou horários de atendimento.");
        return text.toString();
    }

    private static String blankToNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }

    static String unavailable(Logger log, Exception exception) {
        log.log(Level.WARNING, "Falha ao acessar a agenda", exception);
        return UNAVAILABLE;
    }

    static String unexpected(Logger log, Exception exception) {
        log.log(Level.SEVERE, "Erro inesperado ao acessar a agenda", exception);
        return UNEXPECTED;
    }

    static Instant parseOptionalDate(String raw, ZoneId zone, boolean startOfRange) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        try {
            LocalDate date = LocalDate.parse(trimmed);
            LocalDate bound = startOfRange ? date : date.plusDays(1);
            return bound.atStartOfDay(zone).toInstant();
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "Não entendi a data \"" + trimmed
                            + "\". Use o formato AAAA-MM-DD, por exemplo 2026-10-13.");
        }
    }

    static Instant parseStart(String raw, ZoneId zone) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(
                    "Informe a data e a hora de início, no formato 2026-10-13T09:00.");
        }
        String trimmed = raw.trim();
        try {
            if (hasOffset(trimmed)) {
                return OffsetDateTime.parse(ensureSeconds(trimmed)).toInstant();
            }
            return LocalDateTime.parse(ensureSeconds(trimmed)).atZone(zone).toInstant();
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Não entendi a data e hora \"" + trimmed
                    + "\". Use o formato 2026-10-13T09:00, no fuso " + zone.getId() + ".");
        }
    }

    static MeetingMode parseMode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Informe se a reunião é online ou presencial.");
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.equals("online")) {
            return MeetingMode.ONLINE;
        }
        if (value.equals("presencial")) {
            return MeetingMode.IN_PERSON;
        }
        throw new IllegalArgumentException(
                "Não entendi a modalidade \"" + raw.trim() + "\". Use online ou presencial.");
    }

    static String requireOfficeAddress(String officeAddress) {
        if (officeAddress == null || officeAddress.isBlank()) {
            throw new IllegalArgumentException(
                    "Configuração inválida: scheduling.office-address não pode ficar vazio.");
        }
        return officeAddress.trim();
    }

    static List<String> parseEmails(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Informe o e-mail de ao menos um participante.");
        }
        List<String> emails = new ArrayList<>();
        for (String part : raw.split("[,;\\s]+")) {
            if (part.isBlank()) {
                continue;
            }
            String email = part.trim().toLowerCase(Locale.ROOT);
            if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
                throw new IllegalArgumentException("O e-mail \"" + part.trim()
                        + "\" não é válido.");
            }
            if (!emails.contains(email)) {
                emails.add(email);
            }
        }
        if (emails.isEmpty()) {
            throw new IllegalArgumentException("Informe o e-mail de ao menos um participante.");
        }
        return List.copyOf(emails);
    }

    static String requireTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Informe o título da reunião.");
        }
        return title.trim();
    }

    static String requireEventId(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("Informe o identificador da reunião.");
        }
        return eventId.trim();
    }

    static String formatInterval(Instant start, Instant end, ZoneId zone) {
        var zonedStart = start.atZone(zone);
        var zonedEnd = end.atZone(zone);
        return DISPLAY.format(zonedStart) + " até " + CLOCK_TIME.format(zonedEnd) + " ("
                + zone.getId() + ")";
    }

    static String machineStart(Instant start, ZoneId zone) {
        return MACHINE.format(start.atZone(zone));
    }

    static String slotLine(TimeSlot slot, ZoneId zone) {
        return formatInterval(slot.getStart(), slot.getEnd(),
                zone) + " (início: " + machineStart(slot.getStart(), zone)
                + ")";
    }

    static String availability(List<TimeSlot> slots, SchedulingRules rules) {
        StringBuilder text = new StringBuilder("Horários livres em ")
                .append(rules.getTimezone().getId()).append(":");
        for (TimeSlot slot : slots) {
            text.append("\n- ").append(slotLine(slot, rules.getTimezone()));
        }
        return text.toString();
    }

    static String suggestions(List<TimeSlot> slots, SchedulingRules rules) {
        ZoneId zone = rules.getTimezone();
        if (!rules.isPresentOptionsToUser()) {
            TimeSlot slot = slots.get(0);
            return "Melhor horário em " + zone.getId()
                    + ". Não é preciso perguntar ao cliente: marque diretamente este horário.\n- "
                    + slotLine(slot, zone) + "\nUse o início " + machineStart(slot.getStart(), zone)
                    + " na ferramenta de agendamento.";
        }
        StringBuilder text = new StringBuilder("Opções de horário em ").append(zone.getId())
                .append(". Apresente estas opções ao cliente e aguarde a escolha antes de marcar:");
        int index = 1;
        for (TimeSlot slot : slots) {
            text.append("\n").append(index++).append(". ").append(slotLine(slot, zone));
        }
        text.append("\nEstas são as primeiras opções da janela de ")
                .append(rules.getSearchWindowDays())
                .append(" dias, não a agenda inteira. Se o cliente pedir outro dia dentro "
                        + "dessa janela, chame de novo com fromDate e toDate nesse período e "
                        + "mostre o que voltar. Não diga que a agenda ainda não está aberta nem "
                        + "peça para o cliente ligar ou escrever ao escritório por isso.");
        return text.toString();
    }

    static String alternatives(List<TimeSlot> slots, SchedulingRules rules) {
        if (slots.isEmpty()) {
            return NO_SLOTS;
        }
        return "Outras opções:\n" + suggestions(slots, rules);
    }

    static Optional<TimeSlot> matching(List<TimeSlot> slots, Instant start) {
        return slots.stream().filter(slot -> slot.getStart().equals(start)).findFirst();
    }

    static boolean sameBooking(CalendarEvent event, Instant start, String title,
            List<String> emails) {
        if (!event.isCreatedByDora() || event.isCancelled() || !event.getStart().equals(start)) {
            return false;
        }
        if (!event.getTitle().trim().equalsIgnoreCase(title.trim())) {
            return false;
        }
        Set<String> existing = new HashSet<>();
        for (String email : event.getAttendeeEmails()) {
            existing.add(email.toLowerCase(Locale.ROOT));
        }
        return existing.equals(new HashSet<>(emails));
    }

    static String refusalToChange(CalendarEvent event, SchedulingRules rules, java.time.Clock clock,
            String verb) {
        ZoneId zone = rules.getTimezone();
        if (event.isCancelled()) {
            return "A reunião \"" + event.getTitle() + "\" já foi cancelada.";
        }
        if (!event.isCreatedByDora()) {
            return "Só posso " + verb
                    + " reuniões que eu mesma marquei. Esta reunião não foi criada por mim.";
        }
        if (event.getStart().isBefore(clock.instant())) {
            return "A reunião \"" + event.getTitle() + "\" já aconteceu ("
                    + formatInterval(event.getStart(), event.getEnd(),
                            zone) + "). Não consigo " + verb + " por aqui.";
        }
        if (rules.getMinChangeNoticeHours().isPresent()) {
            int hours = rules.getMinChangeNoticeHours().get();
            Instant limit = clock.instant().plus(Duration.ofHours(hours));
            if (event.getStart().isBefore(limit)) {
                return "Não consigo " + verb + " a reunião \"" + event.getTitle()
                        + "\" com tão pouca antecedência. O mínimo é " + hours
                                + (hours == 1 ? " hora" : " horas")
                        + ". Peça ao cliente para falar direto com os participantes.";
            }
        }
        return null;
    }

    static String linkOf(CalendarEvent event) {
        return event.getConferenceLink() == null
                ? "ainda não disponível" : event.getConferenceLink();
    }

    static String booked(CalendarEvent event, SchedulingRules rules, boolean alreadyExisted,
            MeetingMode mode, String officeAddress, String notes) {
        ZoneId zone = rules.getTimezone();
        String intro = alreadyExisted
                ? "Essa reunião já estava marcada nesse horário. Não criei outra."
                : "Reunião marcada.";
        String place = mode == MeetingMode.IN_PERSON
                ? "Local: " + officeAddress
                : "Link: " + linkOf(event);
        String confirm = mode == MeetingMode.IN_PERSON
                ? "Confirme ao cliente a data, a hora com o fuso, o endereço do escritório "
                        + "e que o convite da agenda foi enviado para "
                        + String.join(", ", event.getAttendeeEmails()) + "."
                : "Confirme ao cliente a data, a hora com o fuso e o link da reunião.";
        String noted = notes == null || notes.isBlank() || alreadyExisted
                ? ""
                : "\nNotas: " + notes.trim()
                        + "\nConfirme ao cliente que o assunto foi anotado na reunião.";
        return intro + "\nTítulo: " + event.getTitle() + "\nData e hora: "
                + formatInterval(event.getStart(), event.getEnd(), zone) + "\n" + place + "\nID: "
                + event.getId() + "\n" + confirm + noted;
    }

    static String moved(CalendarEvent previous, CalendarEvent updated, SchedulingRules rules,
            String officeAddress) {
        ZoneId zone = rules.getTimezone();
        String link = updated.getConferenceLink() != null
                ? updated.getConferenceLink() : previous.getConferenceLink();
        if (link == null) {
            return "Reunião reagendada.\nTítulo: " + previous.getTitle() + "\nHorário anterior: "
                    + formatInterval(previous.getStart(), previous.getEnd(),
                            zone) + "\nNovo horário: "
                    + formatInterval(updated.getStart(), updated.getEnd(),
                            zone) + "\nLocal: " + officeAddress
                    + "\nID: " + previous.getId()
                    + "\nConfirme ao cliente a nova data, a hora com o fuso, o endereço do "
                    + "escritório e que o convite da agenda foi atualizado no e-mail.";
        }
        return "Reunião reagendada.\nTítulo: " + previous.getTitle() + "\nHorário anterior: "
                + formatInterval(previous.getStart(), previous.getEnd(), zone) + "\nNovo horário: "
                + formatInterval(updated.getStart(), updated.getEnd(),
                        zone) + "\nLink: " + link + "\nID: "
                + previous.getId()
                        + "\nConfirme ao cliente a nova data, a hora com o fuso e o link. "
                + "O link da reunião continua o mesmo.";
    }

    private static boolean hasOffset(String value) {
        return value.endsWith("Z") || value.matches(".*[+-]\\d{2}:\\d{2}$");
    }

    private static String ensureSeconds(String value) {
        if (value.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}Z")) {
            return value.substring(0, 16) + ":00Z";
        }
        if (value.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}[+-]\\d{2}:\\d{2}")) {
            return value.substring(0, 16) + ":00" + value.substring(16);
        }
        if (value.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}")) {
            return value + ":00";
        }
        return value;
    }
}
