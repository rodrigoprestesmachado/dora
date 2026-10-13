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

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Input guardrail that stops a prompt injection before Dora answers or calls tools.
 *
 * <p>A second model scores only the client message. A score above {@code 0.7}
 * rejects the message. If the score cannot be read, the message is allowed so a
 * scoring failure does not stop the chat.
 *
 * @author Rodrigo Prestes Machado
 */
@ApplicationScoped
public class PromptInjectionGuard implements InputGuardrail {

    /**
     * Scores at or below this value are treated as safe.
     */
    private static final double REJECTION_THRESHOLD = 0.7;

    /**
     * Marker that {@link DoraAgent} places immediately before the client text.
     */
    private static final String CLIENT_MESSAGE_MARKER = "Mensagem do cliente: ";

    /**
     * First decimal from 0 to 1 found in the detector reply.
     */
    private static final Pattern SCORE = Pattern.compile("\\b(0(?:\\.\\d+)?|1(?:\\.0+)?)\\b");

    /**
     * Model that scores the client message.
     */
    private final PromptInjectionDetectionService detectionService;

    /**
     * Creates the guardrail with the injection detector.
     *
     * @param detectionService model that scores the client message
     */
    public PromptInjectionGuard(PromptInjectionDetectionService detectionService) {
        this.detectionService = detectionService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        String clientMessage = clientMessage(userMessage.singleText());
        String rawScore;
        try {
            rawScore = detectionService.score(clientMessage);
        } catch (RuntimeException exception) {
            Log.warn("Prompt injection check failed; allowing the message", exception);
            return success();
        }
        OptionalDouble score = parseScore(rawScore);
        if (score.isEmpty()) {
            Log.warn("Prompt injection score could not be read; allowing the message");
            return success();
        }
        if (score.getAsDouble() > REJECTION_THRESHOLD) {
            Log.warnf("Prompt injection blocked (score %.2f)", score.getAsDouble());
            return failure("Prompt injection detected");
        }
        return success();
    }

    private static String clientMessage(String userMessage) {
        int marker = userMessage.indexOf(CLIENT_MESSAGE_MARKER);
        if (marker < 0) {
            return userMessage;
        }
        return userMessage.substring(marker + CLIENT_MESSAGE_MARKER.length()).trim();
    }

    private static OptionalDouble parseScore(String text) {
        if (text == null || text.isBlank()) {
            return OptionalDouble.empty();
        }
        Matcher matcher = SCORE.matcher(text);
        if (!matcher.find()) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(Double.parseDouble(matcher.group(1)));
    }
}
