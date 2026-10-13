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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dora.adapter.config.SchedulingConfig;
import dev.dora.domain.model.CalendarEvent;
import dev.dora.domain.model.NewMeeting;
import dev.dora.domain.port.out.CalendarAccessException;
import dev.dora.domain.port.out.CalendarConflictException;
import dev.dora.domain.port.out.CalendarEventNotFoundException;
import dev.dora.domain.port.out.CalendarPort;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@link CalendarPort} backed by the Google Calendar MCP server.
 *
 * <p>The agent's tools never call this client. The bean is registered only when
 * {@code scheduling.calendar-provider=google}. Another provider is another
 * {@link CalendarPort} with the same annotation and a different {@code stringValue}.
 *
 * @author Rodrigo Prestes Machado
 */
@IfBuildProperty(name = "scheduling.calendar-provider", stringValue = "google")
@ApplicationScoped
public class GoogleCalendarMcpAdapter implements CalendarPort {

    private static final int MAX_ATTEMPTS = 2;

    private final McpClient mcpClient;
    private final SchedulingConfig schedulingConfig;
    private final ObjectMapper objectMapper;
    private final GoogleCalendarPayloads payloads;
    private final GoogleCalendarEventMapper mapper;

    /**
     * Creates the adapter.
     *
     * @param mcpClient        client of the {@code google-calendar} MCP server
     * @param schedulingConfig scheduling configuration, used for the reference time zone
     */
    @Inject
    public GoogleCalendarMcpAdapter(@McpClientName("google-calendar") McpClient mcpClient,
            SchedulingConfig schedulingConfig) {
        this.mcpClient = mcpClient;
        this.schedulingConfig = schedulingConfig;
        this.objectMapper = new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        this.payloads = new GoogleCalendarPayloads(objectMapper);
        this.mapper = new GoogleCalendarEventMapper(objectMapper);
    }

    @Override
    public List<CalendarEvent> listEvents(Instant from, Instant to) {
        ZoneId zone = zone();
        String json = call("list-events", payloads.listEvents(from, to, zone));
        return mapper.parseEvents(json, zone);
    }

    @Override
    public Optional<CalendarEvent> findEvent(String eventId) {
        try {
            String json = call("get-event", payloads.getEvent(eventId));
            return Optional.of(mapper.parseEvent(json, zone()));
        } catch (CalendarEventNotFoundException exception) {
            return Optional.empty();
        }
    }

    @Override
    public CalendarEvent createEvent(NewMeeting meeting, boolean notifyAttendees) {
        ZoneId zone = zone();
        String json = call("create-event", payloads.createEvent(meeting, zone, notifyAttendees));
        if (isPlainConflict(json)) {
            throw new CalendarConflictException("O calendário recusou o horário.");
        }
        return mapper.parseEvent(json, zone);
    }

    @Override
    public CalendarEvent updateEventTime(String eventId, Instant start, Instant end,
            boolean notifyAttendees) {
        ZoneId zone = zone();
        String json = call("update-event", payloads.updateTime(eventId, start, end, zone,
                notifyAttendees));
        if (isPlainConflict(json)) {
            throw new CalendarConflictException("O calendário recusou o novo horário.");
        }
        return mapper.parseEvent(json, zone);
    }

    @Override
    public void cancelEvent(String eventId, boolean notifyAttendees) {
        String json = call("delete-event", payloads.deleteEvent(eventId, notifyAttendees));
        mapper.requireDeleted(json);
    }

    private String call(String toolName, ObjectNode arguments) {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name(toolName)
                .arguments(arguments.toString())
                .build();
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                ToolExecutionResult result = mcpClient.executeTool(request);
                if (result.isError()) {
                    throw failure(result.resultText());
                }
                return result.resultText() == null ? "" : result.resultText();
            } catch (CalendarEventNotFoundException | CalendarConflictException
                    | CalendarAccessException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                lastFailure = exception;
                Log.warn("Falha ao chamar a ferramenta " + toolName + " do calendário (tentativa "
                        + attempt + ")");
            }
        }
        throw new CalendarAccessException("Não foi possível falar com o calendário.", lastFailure);
    }

    private ZoneId zone() {
        return ZoneId.of(schedulingConfig.timezone());
    }

    private static RuntimeException failure(String text) {
        String body = text == null ? "" : text.toLowerCase(Locale.ROOT);
        Log.warn("O calendário recusou a operação.");
        if (body.contains("not found") || body.contains("404") || body.contains("deleted")
                || body.contains("gone")) {
            return new CalendarEventNotFoundException("Evento não encontrado.");
        }
        if (body.contains("duplicate") || body.contains("conflict")) {
            return new CalendarConflictException("Horário ocupado.");
        }
        return new CalendarAccessException("O calendário recusou a operação.");
    }

    private static boolean isPlainConflict(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("{")) {
            return false;
        }
        String body = trimmed.toLowerCase(Locale.ROOT);
        return body.contains("duplicate") || body.contains("conflict");
    }
}
