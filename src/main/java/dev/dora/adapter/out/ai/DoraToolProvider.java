/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */

package dev.dora.adapter.out.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registers {@link DoraTools} with the descriptions from {@link DoraToolTexts}.
 *
 * @author Rodrigo Prestes Machado
 */
final class DoraToolProvider implements ToolProvider {

    private static final ObjectMapper ARGUMENTS = new ObjectMapper();

    private static final TypeReference<Map<String, Object>> ARGUMENT_MAP = new ArgumentMap();

    private static final List<ToolDefinition> DEFINITIONS = List.of(
            new ToolDefinition("lookupTjrsProcess", List.of(
                    new Parameter("processNumber", ParameterKind.STRING, false))),
            new ToolDefinition("searchTjrsProcessesByPerson", List.of(
                    new Parameter("personName", ParameterKind.STRING, false))),
            // check_availability is deliberately not offered to the model: it lists every
            // free slot and ignores scheduling.max-options-to-present. Only
            // suggest_meeting_slots may present times to the client.
            new ToolDefinition("suggest_meeting_slots", List.of(
                    new Parameter("fromDate", ParameterKind.STRING, true),
                    new Parameter("toDate", ParameterKind.STRING, true),
                    new Parameter("excludeEventId", ParameterKind.STRING, true))),
            new ToolDefinition("schedule_meeting", List.of(
                    new Parameter("startDateTime", ParameterKind.STRING, true),
                    new Parameter("title", ParameterKind.STRING, true),
                    new Parameter("attendeeEmails", ParameterKind.STRING_LIST, true),
                    new Parameter("mode", ParameterKind.STRING, true),
                    new Parameter("notes", ParameterKind.STRING, true))),
            new ToolDefinition("find_meetings", List.of(
                    new Parameter("attendeeEmail", ParameterKind.STRING, true),
                    new Parameter("fromDate", ParameterKind.STRING, true),
                    new Parameter("toDate", ParameterKind.STRING, true),
                    new Parameter("titleQuery", ParameterKind.STRING, true))),
            new ToolDefinition("reschedule_meeting", List.of(
                    new Parameter("eventId", ParameterKind.STRING, true),
                    new Parameter("newStartDateTime", ParameterKind.STRING, true))),
            new ToolDefinition("cancel_meeting", List.of(
                    new Parameter("eventId", ParameterKind.STRING, true),
                    new Parameter("reason", ParameterKind.STRING, true),
                    new Parameter("confirmedByUser", ParameterKind.BOOLEAN, true))));

    private final DoraTools tools;
    private final DoraToolTexts texts;
    private final List<ToolSpecification> specifications;
    private final Map<String, ToolHandler> handlers;

    /**
     * Creates the provider and checks that every text in the file is used.
     *
     * @param tools the tool methods
     * @param texts descriptions loaded from the prompt file
     */
    DoraToolProvider(DoraTools tools, DoraToolTexts texts) {
        this.tools = tools;
        this.texts = texts;
        this.specifications = new ArrayList<>();
        Map<String, ToolHandler> bound = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        for (ToolDefinition definition : DEFINITIONS) {
            used.add(definition.name());
            texts.text(definition.name());
            specifications.add(specification(definition, used));
            bound.put(definition.name(), handler(definition.name()));
        }
        this.handlers = Map.copyOf(bound);
        if (!used.equals(texts.keys())) {
            Set<String> missing = new HashSet<>(used);
            missing.removeAll(texts.keys());
            Set<String> extra = new HashSet<>(texts.keys());
            extra.removeAll(used);
            throw new IllegalStateException(
                    "Textos de ferramenta divergentes. Faltando: " + missing + ". Sobrando: "
                            + extra + ".");
        }
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        ToolProviderResult.Builder builder = ToolProviderResult.builder();
        for (ToolSpecification specification : specifications) {
            builder.add(specification, (toolRequest, memoryId) -> invoke(toolRequest, memoryId));
        }
        return builder.build();
    }

    private String invoke(ToolExecutionRequest request, Object memoryId) {
        ToolHandler bound = handlers.get(request.name());
        if (bound == null) {
            throw new IllegalStateException("Ferramenta sem implementação: " + request.name());
        }
        return bound.call(arguments(request), memoryId);
    }

    private ToolSpecification specification(ToolDefinition definition, Set<String> used) {
        return ToolSpecification.builder()
                .name(definition.name())
                .description(texts.text(definition.name()))
                .parameters(parameters(definition, used))
                .build();
    }

    private JsonObjectSchema parameters(ToolDefinition definition, Set<String> used) {
        JsonObjectSchema.Builder builder = JsonObjectSchema.builder();
        List<String> required = new ArrayList<>();
        for (Parameter parameter : definition.parameters()) {
            String description = null;
            if (parameter.documented()) {
                String key = definition.name() + "." + parameter.name();
                used.add(key);
                description = texts.text(key);
            }
            addParameter(builder, parameter, description);
            required.add(parameter.name());
        }
        return builder.required(required).build();
    }

    private static void addParameter(JsonObjectSchema.Builder builder, Parameter parameter,
            String description) {
        switch (parameter.kind()) {
            case STRING -> addString(builder, parameter.name(), description);
            case BOOLEAN -> builder.addBooleanProperty(parameter.name(), description);
            case STRING_LIST -> builder.addProperty(parameter.name(), JsonArraySchema.builder()
                    .description(description)
                    .items(JsonStringSchema.builder().build())
                    .build());
            default -> throw new IllegalStateException("Tipo não mapeado: " + parameter.kind());
        }
    }

    private static void addString(JsonObjectSchema.Builder builder, String name,
            String description) {
        if (description == null) {
            builder.addStringProperty(name);
        } else {
            builder.addStringProperty(name, description);
        }
    }

    private ToolHandler handler(String name) {
        return switch (name) {
            case "lookupTjrsProcess" -> (args, memoryId) -> tools.lookupTjrsProcess(
                    text(args, "processNumber"), memoryId);
            case "searchTjrsProcessesByPerson" -> (args, memoryId) ->
                    tools.searchTjrsProcessesByPerson(text(args, "personName"), memoryId);
            case "suggest_meeting_slots" -> (args, memoryId) -> tools.suggestMeetingSlots(
                    text(args, "fromDate"), text(args, "toDate"), text(args, "excludeEventId"));
            case "schedule_meeting" -> (args, memoryId) -> tools.scheduleMeeting(
                    text(args, "startDateTime"), text(args, "title"),
                    emails(args, "attendeeEmails"), text(args, "mode"), text(args, "notes"));
            case "find_meetings" -> (args, memoryId) -> tools.findMeetings(
                    text(args, "attendeeEmail"), text(args, "fromDate"), text(args, "toDate"),
                    text(args, "titleQuery"));
            case "reschedule_meeting" -> (args, memoryId) -> tools.rescheduleMeeting(
                    text(args, "eventId"), text(args, "newStartDateTime"));
            case "cancel_meeting" -> (args, memoryId) -> tools.cancelMeeting(
                    text(args, "eventId"), text(args, "reason"), flag(args, "confirmedByUser"));
            default -> throw new IllegalStateException("Ferramenta sem implementação: " + name);
        };
    }

    private static Map<String, Object> arguments(ToolExecutionRequest request) {
        String raw = request.arguments();
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return ARGUMENTS.readValue(raw, ARGUMENT_MAP);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Argumentos inválidos para a ferramenta " + request.name() + ".", exception);
        }
    }

    private static String text(Map<String, Object> args, String name) {
        Object value = args.get(name);
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    private static boolean flag(Map<String, Object> args, String name) {
        Object value = args.get(name);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return false;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private static List<String> emails(Map<String, Object> args, String name) {
        Object value = args.get(name);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("O parâmetro " + name + " deve ser uma lista.");
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                result.add(item.toString());
            }
        }
        return result;
    }

    private static final class ArgumentMap extends TypeReference<Map<String, Object>> {
    }

    private enum ParameterKind {
        STRING,
        BOOLEAN,
        STRING_LIST
    }

    private record Parameter(String name, ParameterKind kind, boolean documented) {
    }

    private record ToolDefinition(String name, List<Parameter> parameters) {
    }

    @FunctionalInterface
    private interface ToolHandler {
        String call(Map<String, Object> args, Object memoryId);
    }
}
