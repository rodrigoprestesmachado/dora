/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */

package dev.dora.adapter.out.whatsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dora.domain.port.out.MessageSenderPort;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Implementation of {@link MessageSenderPort} using the WhatsApp Cloud API (Meta).
 *
 * @see <a href="https://developers.facebook.com/docs/whatsapp/cloud-api/reference/messages">WhatsApp Cloud API — Messages</a>
 * @author Rodrigo Prestes Machado
 */
@ApplicationScoped
public class WhatsAppMessageSender implements MessageSenderPort {

    /** Base URL of the WhatsApp Graph API. */
    private static final String WHATSAPP_API_BASE = "https://graph.facebook.com/v21.0";

    /** Blocking HTTP client for all outgoing WhatsApp API calls. */
    private final HttpClient httpClient;
    /** Jackson mapper for building and parsing JSON request/response bodies. */
    private final ObjectMapper objectMapper;

    /**
     * Default business phone number ID; can be overridden per-call using the webhook metadata
     * value.
     */
    @ConfigProperty(name = "whatsapp.phone-number-id")
    Optional<String> phoneNumberId;

    /** WhatsApp Cloud API access token used to authenticate all outgoing requests. */
    @ConfigProperty(name = "whatsapp.access-token")
    Optional<String> accessToken;

    /**
     * Creates a WhatsAppMessageSender; initialises the HTTP client with a 10-second connect
     * timeout.
     *
     * @param objectMapper Jackson mapper for JSON serialisation
     */
    @Inject
    public WhatsAppMessageSender(ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean sendTextMessage(String to, String text, String overridePhoneId) {
        String effectivePhoneId = resolvePhoneId(overridePhoneId);
        String token = accessToken.orElse("");
        if (effectivePhoneId.isBlank() || token.isBlank()) {
            Log.warn("WhatsApp not configured: phone-number-id or access-token missing");
            return false;
        }
        if (to == null || to.isBlank() || text == null || text.isBlank()) {
            Log.warn("Invalid parameters for WhatsApp send: to or text is blank");
            return false;
        }

        String normalizedTo = normalizeBrazilianMobile(to.replaceAll("[^0-9]", ""));

        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("messaging_product", "whatsapp");
            body.put("recipient_type", "individual");
            body.put("to", normalizedTo);
            body.put("type", "text");
            body.putObject("text").put("body", text);

            HttpResponse<String> response = post(effectivePhoneId, body, Duration.ofSeconds(15));

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                Log.info("WhatsApp message sent successfully to " + to);
                return true;
            }
            Log.error("Failed to send WhatsApp message: " + response.statusCode() + " - "
                    + response.body());
            return false;
        } catch (Exception e) {
            Log.error("Error sending WhatsApp message", e);
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean sendTypingIndicator(String to, String messageId, String overridePhoneId) {
        String effectivePhoneId = resolvePhoneId(overridePhoneId);
        if (effectivePhoneId.isBlank() || accessToken.orElse("").isBlank()) {
            Log.warn("WhatsApp not configured: phone-number-id or access-token missing — "
                    + "typing indicator skipped");
            return false;
        }
        if (messageId == null || messageId.isBlank()) {
            return false;
        }

        boolean readReceiptSent = sendReadReceipt(effectivePhoneId, messageId);
        boolean typingSent = sendReaction(to, messageId, "⏳", overridePhoneId);
        return readReceiptSent || typingSent;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean sendReaction(String to, String messageId, String emoji, String overridePhoneId) {
        String effectivePhoneId = resolvePhoneId(overridePhoneId);
        if (effectivePhoneId.isBlank() || accessToken.orElse("").isBlank()) {
            return false;
        }
        if (to == null || to.isBlank() || messageId == null || messageId.isBlank()) {
            return false;
        }

        String normalizedTo = normalizeBrazilianMobile(to.replaceAll("[^0-9]", ""));

        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("messaging_product", "whatsapp");
            body.put("recipient_type", "individual");
            body.put("to", normalizedTo);
            body.put("type", "reaction");
            body.putObject("reaction")
                    .put("message_id", messageId)
                    .put("emoji", emoji);

            HttpResponse<String> response = post(effectivePhoneId, body, Duration.ofSeconds(10));

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return true;
            }
            Log.warn("WhatsApp reaction failed: " + response.statusCode() + " - "
                    + response.body());
            return false;
        } catch (Exception e) {
            Log.warn("Error sending reaction: " + e.getMessage());
            return false;
        }
    }

    /**
     * Marks an incoming message as read, causing blue double-check marks on the user's device.
     *
     * @param effectivePhoneId business phone number ID
     * @param messageId        WhatsApp message ID (WAMID) to mark as read
     * @return {@code true} if the API call succeeded
     */
    private boolean sendReadReceipt(String effectivePhoneId, String messageId) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("messaging_product", "whatsapp");
            body.put("status", "read");
            body.put("message_id", messageId);

            HttpResponse<String> response = post(effectivePhoneId, body, Duration.ofSeconds(10));
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            Log.warn("Error sending read receipt: " + e.getMessage());
            return false;
        }
    }

    /**
     * Posts a JSON body to the {@code /messages} endpoint of the given business phone number.
     *
     * @param effectivePhoneId business phone number ID
     * @param body             JSON body to send
     * @param timeout          request timeout
     * @return the HTTP response
     */
    private HttpResponse<String> post(String effectivePhoneId, ObjectNode body,
            Duration timeout) throws Exception {
        String jsonBody = objectMapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(WHATSAPP_API_BASE + "/" + effectivePhoneId + "/messages"))
                .timeout(timeout)
                .header("Authorization", "Bearer " + accessToken.orElse(""))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * Resolves which business phone number ID to use: the one from the incoming webhook
     * (multi-number support), falling back to the statically configured one.
     *
     * @param overridePhoneId phone number ID from the webhook metadata, or {@code null}
     * @return the phone number ID to use for outgoing calls
     */
    private String resolvePhoneId(String overridePhoneId) {
        return (overridePhoneId != null && !overridePhoneId.isBlank())
                ? overridePhoneId
                : phoneNumberId.orElse("");
    }

    /**
     * Normalizes Brazilian mobile numbers by adding the 9th digit when missing.
     * WhatsApp Cloud API sometimes delivers BR numbers in the old 8-digit format
     * (55 + XX + 8 digits) while the allowed list uses the 9-digit format
     * (55 + XX + 9XXXXXXXX).
     *
     * @param number digits-only phone number
     * @return the normalized number
     */
    static String normalizeBrazilianMobile(String number) {
        if (number != null && number.matches("^55\\d{2}[6-9]\\d{7}$")) {
            return number.substring(0, 4) + "9" + number.substring(4);
        }
        return number;
    }
}
