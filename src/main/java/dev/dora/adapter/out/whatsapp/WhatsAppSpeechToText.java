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
import dev.dora.domain.port.out.SpeechToTextPort;
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
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Implementation of {@link SpeechToTextPort} using the OpenAI Whisper API. This adapter is
 * independent from the Ollama-based chat model used by {@code DoraAgent}; it only needs an
 * OpenAI API key so that WhatsApp voice notes can be transcribed before being handed to the
 * regular chat pipeline.
 *
 * @see <a href="https://platform.openai.com/docs/api-reference/audio/createTranscription">OpenAI — Create transcription</a>
 * @author Rodrigo Prestes Machado
 */
@ApplicationScoped
public class WhatsAppSpeechToText implements SpeechToTextPort {

    /** Base URL of the OpenAI REST API. */
    private static final String OPENAI_API_BASE = "https://api.openai.com/v1";

    /** Blocking HTTP client used to call the Whisper transcription endpoint. */
    private final HttpClient httpClient;
    /** Jackson mapper for parsing the JSON response from OpenAI. */
    private final ObjectMapper objectMapper;

    /** OpenAI API key used solely for audio transcription. */
    @ConfigProperty(name = "whatsapp.openai-api-key")
    Optional<String> apiKey;

    /**
     * Creates a WhatsAppSpeechToText; initialises the HTTP client with a 10-second connect timeout.
     *
     * @param objectMapper Jackson mapper for JSON response parsing
     */
    @Inject
    public WhatsAppSpeechToText(ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<String> transcribe(byte[] audioData, String mimeType) {
        String key = apiKey.orElse("");
        if (key.isBlank()) {
            Log.warn("whatsapp.openai-api-key not configured - audio transcription unavailable");
            return Optional.empty();
        }
        if (audioData == null || audioData.length == 0) {
            return Optional.empty();
        }

        String extension = extensionFromMimeType(mimeType);
        String filename = "audio." + extension;

        try {
            String boundary = "----DoraWhatsAppBoundary" + UUID.randomUUID().toString().replace("-",
                    "");
            byte[] multipartBody = buildMultipartBody(boundary, audioData, filename, mimeType);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OPENAI_API_BASE + "/audio/transcriptions"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                Log.error("Whisper transcription failed: " + response.statusCode() + " - "
                        + response.body());
                return Optional.empty();
            }

            JsonNode json = objectMapper.readTree(response.body());
            String text = json.has("text") ? json.get("text").asText().trim() : null;
            if (text == null || text.isBlank()) {
                Log.warn("Whisper transcription returned empty text");
                return Optional.empty();
            }

            Log.info("Audio transcribed: " + text.length() + " characters");
            return Optional.of(text);
        } catch (Exception e) {
            Log.error("Error transcribing audio", e);
            return Optional.empty();
        }
    }

    /**
     * Derives the audio file extension from a MIME type string.
     *
     * @param mimeType MIME type (e.g. {@code audio/ogg; codecs=opus})
     * @return a short file extension such as {@code ogg}, {@code mp3}, {@code wav}, {@code webm}
     *     or {@code m4a}
     */
    private String extensionFromMimeType(String mimeType) {
        if (mimeType == null) {
            return "ogg";
        }
        if (mimeType.contains("ogg") || mimeType.contains("opus")) {
            return "ogg";
        }
        if (mimeType.contains("mp3") || mimeType.contains("mpeg")) {
            return "mp3";
        }
        if (mimeType.contains("wav")) {
            return "wav";
        }
        if (mimeType.contains("webm")) {
            return "webm";
        }
        if (mimeType.contains("m4a") || mimeType.contains("mp4")) {
            return "m4a";
        }
        return "ogg";
    }

    /**
     * Builds the multipart/form-data body required by the OpenAI Whisper endpoint.
     *
     * @param boundary  MIME boundary string
     * @param audioData raw bytes of the audio file
     * @param filename  filename sent in the form part (e.g. {@code audio.ogg})
     * @param mimeType  MIME type of the audio (e.g. {@code audio/ogg})
     * @return the encoded multipart body as a byte array
     */
    private byte[] buildMultipartBody(String boundary, byte[] audioData, String filename,
            String mimeType) {
        String contentType = "audio/ogg";
        if (mimeType != null && !mimeType.isBlank()) {
            int semicolon = mimeType.indexOf(';');
            contentType = (semicolon > 0) ? mimeType.substring(0,
                    semicolon).trim() : mimeType.trim();
        }
        String part1 = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n";
        String part2 = "\r\n--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"model\"\r\n\r\n"
                + "whisper-1\r\n"
                + "--" + boundary + "--\r\n";

        byte[] part1Bytes = part1.getBytes(StandardCharsets.UTF_8);
        byte[] part2Bytes = part2.getBytes(StandardCharsets.UTF_8);

        byte[] result = new byte[part1Bytes.length + audioData.length + part2Bytes.length];
        System.arraycopy(part1Bytes, 0, result, 0, part1Bytes.length);
        System.arraycopy(audioData, 0, result, part1Bytes.length, audioData.length);
        System.arraycopy(part2Bytes, 0, result, part1Bytes.length + audioData.length,
                part2Bytes.length);

        return result;
    }
}
