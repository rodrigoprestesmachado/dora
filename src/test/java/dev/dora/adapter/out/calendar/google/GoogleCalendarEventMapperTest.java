package dev.dora.adapter.out.calendar.google;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dora.domain.model.CalendarEvent;
import dev.dora.domain.model.MeetingMode;
import dev.dora.domain.model.NewMeeting;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Parser and request tests for the Google Calendar MCP adapter. No network.
 *
 * @author Rodrigo Prestes Machado
 */
class GoogleCalendarEventMapperTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final ObjectMapper objectMapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    private final GoogleCalendarEventMapper mapper = new GoogleCalendarEventMapper(objectMapper);
    private final GoogleCalendarPayloads payloads = new GoogleCalendarPayloads(objectMapper);

    @Test
    void readsEventsCreatedByDoraAndAllDayTentativeAndFreeOnes() {
        String json = """
                {
                  "events": [
                    {
                      "id": "evt1",
                      "summary": "Consulta",
                      "start": {"dateTime": "2026-10-13T09:00:00-03:00",
                              "timeZone": "America/Sao_Paulo"}, 
                      "end": {"dateTime": "2026-10-13T10:00:00-03:00",
                              "timeZone": "America/Sao_Paulo"}, 
                      "status": "confirmed",
                      "attendees": [{"email": "ana@escritorio.com"}],
                      "hangoutLink": "https://meet.google.com/abc-defg-hij",
                      "transparency": "opaque",
                      "extendedProperties": {"private": {"doraCreated": "true"}}
                    },
                    {
                      "id": "all-day",
                      "summary": "Feriado",
                      "start": {"date": "2026-10-13"},
                      "end": {"date": "2026-10-14"},
                      "status": "confirmed"
                    },
                    {
                      "id": "maybe",
                      "summary": "Talvez",
                      "start": {"dateTime": "2026-10-13T11:00:00", "timeZone": "America/Sao_Paulo"},
                      "end": {"dateTime": "2026-10-13T12:00:00", "timeZone": "America/Sao_Paulo"},
                      "status": "tentative"
                    },
                    {
                      "id": "free",
                      "summary": "Livre",
                      "start": {"dateTime": "2026-10-13T14:00:00-03:00"},
                      "end": {"dateTime": "2026-10-13T15:00:00-03:00"},
                      "status": "confirmed",
                      "transparency": "transparent",
                      "conferenceData": {"entryPoints": [{"entryPointType": "video", "uri": "https://meet.google.com/from-entry"}]}
                    }
                  ]
                }
                """;

        List<CalendarEvent> events = mapper.parseEvents(json, ZONE);

        CalendarEvent booked = events.get(0);
        assertEquals("evt1", booked.getId());
        assertTrue(booked.isCreatedByDora());
        assertEquals("https://meet.google.com/abc-defg-hij", booked.getConferenceLink());
        assertEquals(List.of("ana@escritorio.com"), booked.getAttendeeEmails());
        assertFalse(booked.isAllDay());

        CalendarEvent allDay = events.get(1);
        assertTrue(allDay.isAllDay());
        assertEquals(LocalMidnight(), allDay.getStart());
        assertFalse(allDay.isCreatedByDora());

        assertTrue(events.get(2).isTentative());
        assertTrue(events.get(3).isShowAsFree());
        assertEquals("https://meet.google.com/from-entry", events.get(3).getConferenceLink());
    }

    @Test
    void readsASingleEventWrapper() {
        String json = """
                {"event": {"id": "evt2", "summary": "Retorno",
                  "start": {"dateTime": "2026-10-14T09:00:00-03:00"},
                  "end": {"dateTime": "2026-10-14T10:00:00-03:00"},
                  "status": "confirmed"}}
                """;

        CalendarEvent event = mapper.parseEvent(json, ZONE);

        assertEquals("evt2", event.getId());
        assertNull(event.getConferenceLink());
    }

    @Test
    void createPayloadRequestsMeetMarksDoraAndNotifies() {
        NewMeeting meeting = new NewMeeting("Consulta", Instant.parse("2026-10-13T12:00:00Z"),
                Instant.parse("2026-10-13T13:00:00Z"), List.of("ana@escritorio.com"),
                        MeetingMode.ONLINE, null, null);

        ObjectNode payload = payloads.createEvent(meeting, ZONE, true);

        assertEquals("hangoutsMeet", payload.path("conferenceData").path("createRequest")
                .path("conferenceSolutionKey").path("type").asText());
        assertEquals("true",
                payload.path("extendedProperties").path("private").path("doraCreated").asText());
        assertEquals("all", payload.path("sendUpdates").asText());
        assertEquals("Consulta", payload.path("summary").asText());
        assertEquals("ana@escritorio.com", payload.path("attendees").get(0).path("email").asText());
        assertTrue(payload.path("location").isMissingNode());
        assertTrue(payload.path("description").isMissingNode());
        assertFalse(payload.path("conferenceData").path("createRequest").path("requestId")
                .asText().isBlank());
    }

    @Test
    void createPayloadForAnInPersonMeetingStoresTheOfficeAndSkipsMeet() {
        String office = "R. Uruguai, 902 - Centro, Pelotas - RS, 96010-630";
        NewMeeting meeting = new NewMeeting("Ana Souza", Instant.parse("2026-10-13T12:00:00Z"),
                Instant.parse("2026-10-13T13:00:00Z"), List.of("ana@escritorio.com"),
                MeetingMode.IN_PERSON, office, "Quero falar sobre um contrato.");

        ObjectNode payload = payloads.createEvent(meeting, ZONE, true);

        assertEquals(office, payload.path("location").asText());
        assertEquals("Ana Souza", payload.path("summary").asText());
        assertTrue(payload.path("conferenceData").isMissingNode());
        assertEquals("all", payload.path("sendUpdates").asText());
        assertEquals("ana@escritorio.com", payload.path("attendees").get(0).path("email").asText());
        assertEquals("Quero falar sobre um contrato.", payload.path("description").asText());
        assertEquals("true",
                payload.path("extendedProperties").path("private").path("doraCreated").asText());
    }

    @Test
    void updatePayloadChangesOnlyTheTimeAndCanStayQuiet() {
        ObjectNode payload = payloads.updateTime("evt1", Instant.parse("2026-10-14T12:00:00Z"),
                Instant.parse("2026-10-14T13:00:00Z"), ZONE, false);

        assertEquals("evt1", payload.path("eventId").asText());
        assertEquals("none", payload.path("sendUpdates").asText());
        assertFalse(payload.path("checkConflicts").asBoolean(true));
        assertTrue(payload.path("conferenceData").isMissingNode());
        assertTrue(payload.path("extendedProperties").isMissingNode());
    }

    @Test
    void deletePayloadCarriesTheNotificationChoice() {
        assertEquals("all", payloads.deleteEvent("evt1", true).path("sendUpdates").asText());
        assertEquals("none", payloads.deleteEvent("evt1", false).path("sendUpdates").asText());
    }

    private static Instant LocalMidnight() {
        return java.time.LocalDate.of(2026, 10, 13).atStartOfDay(ZONE).toInstant();
    }
}
