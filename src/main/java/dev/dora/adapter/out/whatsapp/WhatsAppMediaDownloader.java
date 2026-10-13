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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dora.domain.port.out.MediaDownloaderPort;
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
 * Implementation of {@link MediaDownloaderPort} using the WhatsApp Cloud API (Meta).
 *
 * @see <a href="https://developers.facebook.com/docs/whatsapp/cloud-api/reference/media">WhatsApp Cloud API — Media</a>
 * @author Rodrigo Prestes Machado
 */
@ApplicationScoped
public class WhatsAppMediaDownloader implements MediaDownloaderPort {

    /** Base URL of the WhatsApp Graph API. */
    private static final String WHATSAPP_API_BASE = "https://graph.facebook.com/v21.0";

    /** Blocking HTTP client for the two-step media download (metadata → binary). */
    private final HttpClient httpClient;
    /** Jackson mapper for parsing the JSON metadata response from the Graph API. */
    private final ObjectMapper objectMapper;

    /** WhatsApp Cloud API access token used to authenticate all requests. */
    @ConfigProperty(name = "whatsapp.access-token")
    Optional<String> accessToken;

    /**
     * Creates a WhatsAppMediaDownloader; initialises the HTTP client with a 10-second connect
     * timeout.
     *
     * @param objectMapper Jackson mapper for JSON parsing
     */
    @Inject
    public WhatsAppMediaDownloader(ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<byte[]> downloadMedia(String mediaId) {
        String token = accessToken.orElse("");
        if (token.isBlank()) {
            Log.warn("WhatsApp access-token missing - unable to download media");
            return Optional.empty();
        }
        if (mediaId == null || mediaId.isBlank()) {
            return Optional.empty();
        }

        try {
            HttpRequest metaRequest = HttpRequest.newBuilder()
                    .uri(URI.create(WHATSAPP_API_BASE + "/" + mediaId))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();

            HttpResponse<String> metaResponse = httpClient.send(metaRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (metaResponse.statusCode() != 200) {
                Log.error("Failed to get WhatsApp media URL: "
                        + metaResponse.statusCode() + " - " + metaResponse.body());
                return Optional.empty();
            }

            JsonNode json = objectMapper.readTree(metaResponse.body());
            String url = json.has("url") ? json.get("url").asText() : null;
            if (url == null || url.isBlank()) {
                Log.error("WhatsApp media response has no URL");
                return Optional.empty();
            }

            HttpRequest downloadRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();

            HttpResponse<byte[]> downloadResponse = httpClient.send(downloadRequest,
                    HttpResponse.BodyHandlers.ofByteArray());
            if (downloadResponse.statusCode() != 200) {
                Log.error("Failed to download WhatsApp media: " + downloadResponse.statusCode());
                return Optional.empty();
            }

            byte[] data = downloadResponse.body();
            if (data == null || data.length == 0) {
                Log.warn("Empty WhatsApp media");
                return Optional.empty();
            }

            Log.info("WhatsApp media downloaded: " + data.length + " bytes");
            return Optional.of(data);
        } catch (Exception e) {
            Log.error("Error downloading WhatsApp media", e);
            return Optional.empty();
        }
    }
}
