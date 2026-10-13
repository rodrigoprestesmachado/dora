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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dora.domain.model.MeetingMode;
import dev.dora.domain.model.NewMeeting;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * JSON arguments for the Google Calendar MCP tools. Only this provider knows those tool names.
 *
 * @author Rodrigo Prestes Machado
 */
final class GoogleCalendarPayloads {

    static final String CALENDAR_ID = "primary";

    /** Private extended property that marks an event as created by Dora. */
    static final String DORA_CREATED_KEY = "doraCreated";

    private static final DateTimeFormatter ISO_OFFSET =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final ObjectMapper objectMapper;

    GoogleCalendarPayloads(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ObjectNode listEvents(Instant from, Instant to, ZoneId zone) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("calendarId", CALENDAR_ID);
        node.put("timeMin", format(from, zone));
        node.put("timeMax", format(to, zone));
        node.put("timeZone", zone.getId());
        return node;
    }

    ObjectNode getEvent(String eventId) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("calendarId", CALENDAR_ID);
        node.put("eventId", eventId);
        return node;
    }

    /**
     * Creates an event marked as Dora's. Attendees are listed in both modes, so the calendar
     * invitation is sent when {@code notify} is true. An online meeting also requests a Google
     * Meet link. An in-person meeting stores the office address and does not request a link.
     * {@code notify} becomes {@code sendUpdates=all} or {@code none}.
     */
    ObjectNode createEvent(NewMeeting meeting, ZoneId zone, boolean notify) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("calendarId", CALENDAR_ID);
        node.put("summary", meeting.getTitle());
        node.put("start", format(meeting.getStart(), zone));
        node.put("end", format(meeting.getEnd(), zone));
        node.put("timeZone", zone.getId());
        node.put("sendUpdates", notify ? "all" : "none");
        node.put("allowDuplicates", false);
        if (!meeting.getAttendeeEmails().isEmpty()) {
            ArrayNode attendees = node.putArray("attendees");
            for (String email : meeting.getAttendeeEmails()) {
                attendees.addObject().put("email", email);
            }
        }
        if (meeting.getMode() == MeetingMode.ONLINE) {
            ObjectNode conference = node.putObject("conferenceData").putObject("createRequest");
            conference.put("requestId", UUID.randomUUID().toString());
            conference.putObject("conferenceSolutionKey").put("type", "hangoutsMeet");
        } else {
            node.put("location", meeting.getLocation());
        }
        if (meeting.getNotes() != null) {
            node.put("description", meeting.getNotes());
        }
        node.putObject("extendedProperties").putObject("private").put(DORA_CREATED_KEY, "true");
        return node;
    }

    /**
     * Moves an event by patching only the interval. Conference data is omitted so the Meet link
     * stays.
     */
    ObjectNode updateTime(String eventId, Instant start, Instant end, ZoneId zone, boolean notify) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("calendarId", CALENDAR_ID);
        node.put("eventId", eventId);
        node.put("start", format(start, zone));
        node.put("end", format(end, zone));
        node.put("timeZone", zone.getId());
        node.put("sendUpdates", notify ? "all" : "none");
        node.put("checkConflicts", false);
        return node;
    }

    ObjectNode deleteEvent(String eventId, boolean notify) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("calendarId", CALENDAR_ID);
        node.put("eventId", eventId);
        node.put("sendUpdates", notify ? "all" : "none");
        return node;
    }

    private static String format(Instant instant, ZoneId zone) {
        return ISO_OFFSET.format(instant.atZone(zone));
    }
}
