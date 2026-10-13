/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */

package dev.dora.adapter.out.calendar.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dora.domain.model.CalendarEvent;
import dev.dora.domain.port.out.CalendarAccessException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the JSON text returned by the Google Calendar MCP server into domain events.
 *
 * @author Rodrigo Prestes Machado
 */
public final class GoogleCalendarEventMapper {

    private final ObjectMapper objectMapper;

    /**
     * Creates a mapper.
     *
     * @param objectMapper JSON parser
     */
    public GoogleCalendarEventMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parses a list response, or a single-event response, into domain events.
     * Entries that have no usable interval are skipped.
     *
     * @param json MCP tool text
     * @param zone zone used when the payload has a date without a time
     * @return the events that could be read
     */
    public List<CalendarEvent> parseEvents(String json, ZoneId zone) {
        JsonNode root = read(json);
        List<CalendarEvent> events = new ArrayList<>();
        if (root.has("events") && root.get("events").isArray()) {
            for (JsonNode node : root.get("events")) {
                CalendarEvent event = toEvent(node, zone);
                if (event != null) {
                    events.add(event);
                }
            }
            return List.copyOf(events);
        }
        JsonNode single = root.has("event") ? root.get("event") : root;
        CalendarEvent event = toEvent(single, zone);
        if (event == null) {
            throw new CalendarAccessException("Resposta do calendário sem evento utilizável.");
        }
        return List.of(event);
    }

    /**
     * Parses the event from a get, create or update response.
     *
     * @param json MCP tool text
     * @param zone zone used when the payload has a date without a time
     * @return the event
     */
    public CalendarEvent parseEvent(String json, ZoneId zone) {
        return parseEvents(json, zone).get(0);
    }

    /**
     * Accepts a delete response only when the provider reports success.
     *
     * @param json MCP tool text
     */
    public void requireDeleted(String json) {
        JsonNode root = read(json);
        if (!root.path("success").asBoolean(false)) {
            throw new CalendarAccessException("O calendário não confirmou o cancelamento.");
        }
    }

    private JsonNode read(String json) {
        if (json == null || json.isBlank()) {
            throw new CalendarAccessException("Resposta vazia do calendário.");
        }
        String body = json.trim();
        int start = body.indexOf('{');
        int end = body.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new CalendarAccessException("Resposta do calendário sem JSON.");
        }
        try {
            return objectMapper.readTree(body.substring(start, end + 1));
        } catch (Exception exception) {
            throw new CalendarAccessException("Resposta do calendário ilegível.", exception);
        }
    }

    private static CalendarEvent toEvent(JsonNode node, ZoneId zone) {
        if (node == null || !node.hasNonNull("id")) {
            return null;
        }
        boolean allDay = node.path("start").hasNonNull("date")
                && !node.path("start").hasNonNull("dateTime");
        Instant start = toInstant(node.get("start"), zone);
        Instant end = toInstant(node.get("end"), zone);
        if (start == null || end == null || !start.isBefore(end)) {
            return null;
        }
        String status = node.path("status").asText("");
        return new CalendarEvent(node.get("id").asText(), node.path("summary").asText(""), start,
                end, allDay, 
                emails(node), conferenceLink(node), createdByDora(node), "cancelled".equals(status),
                "tentative".equals(status),
                        "transparent".equals(node.path("transparency").asText("")));
    }

    private static Instant toInstant(JsonNode node, ZoneId fallback) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.hasNonNull("dateTime")) {
            String value = node.get("dateTime").asText();
            ZoneId zone = node.hasNonNull("timeZone")
                    ? ZoneId.of(node.get("timeZone").asText()) : fallback;
            try {
                if (value.endsWith("Z") || value.matches(".*[+-]\\d{2}:\\d{2}$")) {
                    return OffsetDateTime.parse(value).toInstant();
                }
                return LocalDateTime.parse(value).atZone(zone).toInstant();
            } catch (DateTimeParseException exception) {
                return null;
            }
        }
        if (node.hasNonNull("date")) {
            return LocalDate.parse(node.get("date").asText()).atStartOfDay(fallback).toInstant();
        }
        return null;
    }

    private static List<String> emails(JsonNode node) {
        JsonNode attendees = node.get("attendees");
        if (attendees == null || !attendees.isArray()) {
            return List.of();
        }
        List<String> emails = new ArrayList<>();
        for (JsonNode attendee : attendees) {
            if (attendee.hasNonNull("email")) {
                emails.add(attendee.get("email").asText());
            }
        }
        return emails;
    }

    private static String conferenceLink(JsonNode node) {
        if (node.hasNonNull("hangoutLink") && !node.get("hangoutLink").asText().isBlank()) {
            return node.get("hangoutLink").asText();
        }
        JsonNode entryPoints = node.path("conferenceData").path("entryPoints");
        if (!entryPoints.isArray()) {
            return null;
        }
        String fallback = null;
        for (JsonNode entry : entryPoints) {
            String uri = entry.path("uri").asText("");
            if (uri.isBlank()) {
                continue;
            }
            if ("video".equals(entry.path("entryPointType").asText())) {
                return uri;
            }
            if (fallback == null) {
                fallback = uri;
            }
        }
        return fallback;
    }

    private static boolean createdByDora(JsonNode node) {
        return "true".equals(node.path("extendedProperties").path("private")
                .path(GoogleCalendarPayloads.DORA_CREATED_KEY).asText(""));
    }
}
