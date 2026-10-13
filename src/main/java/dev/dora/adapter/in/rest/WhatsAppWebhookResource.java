/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */

package dev.dora.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dora.adapter.ClientSession;
import dev.dora.domain.port.in.ChatUseCase;
import dev.dora.domain.port.out.MediaDownloaderPort;
import dev.dora.domain.port.out.MessageSenderPort;
import dev.dora.domain.port.out.SpeechToTextPort;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Webhook that receives WhatsApp Cloud API (Meta) messages and forwards them to
 * {@link ChatUseCase}, replying with the assistant's answer.
 *
 * <p>Public endpoint (no authentication) as required by Meta's webhook contract; the POST
 * variant validates the {@code X-Hub-Signature-256} header to reject forged requests.
 *
 * @see <a href="https://developers.facebook.com/docs/whatsapp/cloud-api/webhooks">WhatsApp Webhooks</a>
 * @author Rodrigo Prestes Machado
 */
@Path("/webhook/whatsapp")
@Produces(MediaType.TEXT_PLAIN)
public class WhatsAppWebhookResource {

    /** Use case that generates the assistant reply for each incoming message. */
    private final ChatUseCase chatUseCase;
    /** Port used to send text messages, typing indicators and reactions via WhatsApp. */
    private final MessageSenderPort messageSender;
    /** Port used to download media (e.g. voice notes) from the WhatsApp media server. */
    private final MediaDownloaderPort mediaDownloader;
    /** Port used to transcribe audio data to text before passing it to the chat use case. */
    private final SpeechToTextPort speechToText;
    /** Jackson mapper used to parse the raw webhook body after signature validation. */
    private final ObjectMapper objectMapper;

    /** Token expected in the {@code hub.verify_token} query parameter during webhook setup. */
    @ConfigProperty(name = "whatsapp.verify-token", defaultValue = "dora-webhook-verify")
    String verifyToken;

    /** Meta App Secret used to validate the {@code X-Hub-Signature-256} header. */
    @ConfigProperty(name = "whatsapp.app-secret")
    Optional<String> appSecret;

    /**
     * Constructs the resource with all required ports.
     *
     * @param chatUseCase     use case for generating assistant replies
     * @param messageSender   port for sending messages via WhatsApp
     * @param mediaDownloader port for downloading media attachments
     * @param speechToText    port for transcribing audio to text
     * @param objectMapper    Jackson mapper for parsing the webhook payload
     */
    @Inject
    public WhatsAppWebhookResource(ChatUseCase chatUseCase, MessageSenderPort messageSender,
            MediaDownloaderPort mediaDownloader, SpeechToTextPort speechToText,
                    ObjectMapper objectMapper) {
        this.chatUseCase = chatUseCase;
        this.messageSender = messageSender;
        this.mediaDownloader = mediaDownloader;
        this.speechToText = speechToText;
        this.objectMapper = objectMapper;
    }

    /**
     * Webhook verification (GET), required by Meta when configuring the webhook URL.
     *
     * @param mode      expected to be {@code "subscribe"}
     * @param token     value that must match {@code whatsapp.verify-token}
     * @param challenge value echoed back to Meta on success
     * @return {@code 200} with the challenge body, or {@code 403} if verification fails
     */
    @GET
    public Response verifyWebhook(
            @QueryParam("hub.mode") String mode,
            @QueryParam("hub.verify_token") String token,
            @QueryParam("hub.challenge") String challenge) {
        if ("subscribe".equals(mode) && verifyToken.equals(token) && challenge != null) {
            return Response.ok(challenge).build();
        }
        return Response.status(403).build();
    }

    /**
     * Receives WhatsApp messages (POST). Validates the request signature, then processes
     * the message asynchronously and returns {@code 200} immediately to avoid a Meta timeout.
     *
     * @param rawBody   raw JSON body, needed (before deserialization) to validate the HMAC
     *     signature
     * @param signature value of the {@code X-Hub-Signature-256} header
     * @return {@code 200} on success/ignored payloads, {@code 403} on an invalid signature
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response receiveWebhook(String rawBody,
            @HeaderParam("X-Hub-Signature-256") String signature) {
        if (!isValidSignature(rawBody, signature)) {
            Log.warn("Invalid signature on WhatsApp webhook");
            return Response.status(403).build();
        }

        WhatsAppWebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, WhatsAppWebhookPayload.class);
        } catch (Exception e) {
            Log.error("Invalid payload on WhatsApp webhook", e);
            return Response.ok().build();
        }

        if (payload == null || payload.entry == null) {
            return Response.ok().build();
        }

        for (WhatsAppWebhookPayload.WebhookEntry entry : payload.entry) {
            if (entry.changes == null) {
                continue;
            }
            for (WhatsAppWebhookPayload.WebhookChange change : entry.changes) {
                if (change.value == null || change.value.messages == null) {
                    continue;
                }
                String phoneNumberId = change.value.metadata != null
                        ? change.value.metadata.phoneNumberId : null;

                for (WhatsAppWebhookPayload.WebhookMessage msg : change.value.messages) {
                    handleMessage(msg, change.value.contacts, phoneNumberId);
                }
            }
        }

        return Response.ok().build();
    }

    /**
     * Dispatches a single incoming message to the text or audio handling path.
     *
     * @param msg           incoming message
     * @param contacts      contact profiles from the same change event (for the sender's display
     *     name)
     * @param phoneNumberId business phone number ID from the webhook metadata
     */
    private void handleMessage(WhatsAppWebhookPayload.WebhookMessage msg,
            List<WhatsAppWebhookPayload.WebhookContact> contacts, String phoneNumberId) {
        String from = msg.from;
        String messageId = msg.id;

        if ("text".equals(msg.type) && msg.text != null && msg.text.body != null
                && !msg.text.body.isBlank()) {
            Log.info("WhatsApp message from " + from + ": " + msg.text.body);
            messageSender.sendTypingIndicator(from, messageId, phoneNumberId);
            replyToPrompt(msg.text.body.trim(), from, phoneNumberId, messageId);
        } else if ("audio".equals(msg.type) && msg.audio != null && msg.audio.id != null) {
            Log.info("WhatsApp audio from " + from + " (media id: " + msg.audio.id + ")");
            messageSender.sendTypingIndicator(from, messageId, phoneNumberId);
            transcribeAndReply(msg.audio.id, msg.audio.mimeType, from, phoneNumberId, messageId);
        }
    }

    /**
     * Downloads and transcribes a WhatsApp voice note off the event loop, then replies as if
     * the transcription were a regular text prompt.
     *
     * @param mediaId       WhatsApp media identifier
     * @param mimeType      MIME type of the audio file
     * @param from          sender's WhatsApp number
     * @param phoneNumberId business phone number ID from the webhook metadata
     * @param messageId     identifier of the incoming message (for reactions)
     */
    private void transcribeAndReply(String mediaId, String mimeType, String from,
            String phoneNumberId, 
            String messageId) {
        Uni.createFrom().item(() -> mediaDownloader.downloadMedia(mediaId)
                        .flatMap(data -> speechToText.transcribe(data, mimeType)))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .subscribe().with(
                        transcribedOpt -> {
                            if (transcribedOpt.isEmpty()) {
                                messageSender.sendReaction(from, messageId, "", phoneNumberId);
                                messageSender.sendTextMessage(from,
                                        "Não consegui transcrever o áudio. Tente novamente ou "
                                        + "envie uma "
                                                + "mensagem de texto.",
                                        phoneNumberId);
                            } else {
                                replyToPrompt(transcribedOpt.get(), from, phoneNumberId, messageId);
                            }
                        },
                        failure -> {
                            Log.error("Error processing WhatsApp audio from " + from, failure);
                            messageSender.sendReaction(from, messageId, "", phoneNumberId);
                            messageSender.sendTextMessage(from,
                                    "Desculpe, ocorreu um erro ao processar seu áudio. Tente "
                                    + "novamente.",
                                    phoneNumberId);
                        });
    }

    /**
     * Calls {@link ChatUseCase#chatSync} and sends the complete reply back via WhatsApp.
     *
     * @param prompt        text prompt (typed or transcribed)
     * @param from          recipient's WhatsApp number
     * @param phoneNumberId business phone number ID from the webhook metadata
     * @param messageId     identifier of the incoming message (for reactions)
     */
    private void replyToPrompt(String prompt, String from, String phoneNumberId, String messageId) {
        ClientSession.Registration session = ClientSession.open(from, text -> {
            boolean sent = messageSender.sendTextMessage(from, text, phoneNumberId);
            if (!sent) {
                Log.warn("Não foi possível avisar " + from + " sobre a consulta no TJRS");
            }
        });
        chatUseCase.chatSync(from, prompt)
                .onTermination().invoke(session::close)
                .subscribe().with(
                        reply -> {
                            messageSender.sendReaction(from, messageId, "", phoneNumberId);
                            boolean sent = messageSender.sendTextMessage(from, reply,
                                    phoneNumberId);
                            if (!sent) {
                                Log.warn("Failed to send WhatsApp reply to " + from
                                        + " - check whatsapp.phone-number-id and "
                                        + "whatsapp.access-token");
                            }
                        },
                        failure -> {
                            Log.error("Error generating reply for " + from, failure);
                            messageSender.sendReaction(from, messageId, "", phoneNumberId);
                            messageSender.sendTextMessage(from,
                                    "Desculpe, ocorreu um erro. Tente novamente.", phoneNumberId);
                        });
    }

    /**
     * Validates the {@code X-Hub-Signature-256} header against the raw request body using
     * HMAC-SHA256 and {@code whatsapp.app-secret}. If no app secret is configured, the request
     * is allowed through with a warning (same "optional feature" pattern already used for
     * {@code whatsapp.access-token}).
     *
     * @param rawBody   raw JSON body as received
     * @param signature value of the {@code X-Hub-Signature-256} header (format {@code
     * sha256=<hex>})
     * @return {@code true} if the signature is valid, or if validation is disabled
     */
    private boolean isValidSignature(String rawBody, String signature) {
        String secret = appSecret.orElse("");
        if (secret.isBlank()) {
            Log.warn("whatsapp.app-secret not configured - webhook signature validation disabled");
            return true;
        }
        if (signature == null || !signature.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(
                    (rawBody == null ? "" : rawBody).getBytes(StandardCharsets.UTF_8));
            String expected = "sha256=" + HexFormat.of().formatHex(hash);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.error("Error validating WhatsApp webhook signature", e);
            return false;
        }
    }
}
