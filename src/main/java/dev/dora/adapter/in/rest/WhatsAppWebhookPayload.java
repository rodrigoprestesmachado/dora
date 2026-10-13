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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * DTO for the WhatsApp Cloud API (Meta) webhook payload.
 *
 * @see <a href="https://developers.facebook.com/docs/whatsapp/cloud-api/webhooks/components">WhatsApp Cloud API — Webhooks</a>
 * @author Rodrigo Prestes Machado
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class WhatsAppWebhookPayload {

    /** Fixed value {@code "whatsapp"} identifying the object type in Meta webhooks. */
    @JsonProperty("object")
    public String object;

    /** Top-level list of webhook entries; each entry corresponds to one business account. */
    @JsonProperty("entry")
    public List<WebhookEntry> entry;

    /**
     * One entry in the webhook payload, corresponding to a single WhatsApp Business account.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebhookEntry {
        /** Unique identifier of the WhatsApp Business account. */
        @JsonProperty("id")
        public String id;

        /** List of change events that occurred for this account. */
        @JsonProperty("changes")
        public List<WebhookChange> changes;
    }

    /**
     * A single change event containing the type of field that changed and the new value.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebhookChange {
        /** The changed value object containing messages, metadata and contacts. */
        @JsonProperty("value")
        public WebhookValue value;

        /** Name of the field that triggered this change (usually {@code "messages"}). */
        @JsonProperty("field")
        public String field;
    }

    /**
     * The value object within a change event, containing all relevant message data.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebhookValue {
        /** Messaging product identifier (always {@code "whatsapp"}). */
        @JsonProperty("messaging_product")
        public String messagingProduct;

        /** Metadata about the receiving business phone number. */
        @JsonProperty("metadata")
        public WebhookMetadata metadata;

        /** Contact profiles of the senders involved in this change event. */
        @JsonProperty("contacts")
        public List<WebhookContact> contacts;

        /** List of messages received in this change event. */
        @JsonProperty("messages")
        public List<WebhookMessage> messages;
    }

    /**
     * Metadata describing the business phone number that received the messages.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebhookMetadata {
        /** Human-readable phone number shown to contacts (e.g. {@code +15550001234}). */
        @JsonProperty("display_phone_number")
        public String displayPhoneNumber;

        /** Numeric ID of the business phone number, used when sending replies. */
        @JsonProperty("phone_number_id")
        public String phoneNumberId;
    }

    /**
     * Contact information for a message sender, as provided by the webhook.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebhookContact {
        /** WhatsApp profile of the contact (contains the display name). */
        @JsonProperty("profile")
        public WebhookProfile profile;

        /** WhatsApp ID of the contact (the phone number in E.164 format). */
        @JsonProperty("wa_id")
        public String waId;
    }

    /**
     * Profile information for a WhatsApp contact.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebhookProfile {
        /** Display name set by the contact in their WhatsApp profile. */
        @JsonProperty("name")
        public String name;
    }

    /**
     * A single incoming WhatsApp message within a change event.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebhookMessage {
        /** Sender's WhatsApp number in E.164 format. */
        @JsonProperty("from")
        public String from;

        /** Unique identifier of this message assigned by WhatsApp. */
        @JsonProperty("id")
        public String id;

        /** Unix timestamp (as string) when the message was sent. */
        @JsonProperty("timestamp")
        public String timestamp;

        /** Message type, e.g. {@code "text"}, {@code "audio"}, {@code "image"}. */
        @JsonProperty("type")
        public String type;

        /** Text payload; non-null only when {@code type} is {@code "text"}. */
        @JsonProperty("text")
        public WebhookText text;

        /** Audio payload; non-null only when {@code type} is {@code "audio"}. */
        @JsonProperty("audio")
        public WebhookAudio audio;
    }

    /**
     * Text content of a WhatsApp text message.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebhookText {
        /** Actual text written by the user. */
        @JsonProperty("body")
        public String body;
    }

    /**
     * Audio attachment of a WhatsApp audio message.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebhookAudio {
        /** WhatsApp media identifier used to download the audio via the media API. */
        @JsonProperty("id")
        public String id;

        /** MIME type of the audio file (e.g. {@code audio/ogg; codecs=opus}). */
        @JsonProperty("mime_type")
        public String mimeType;
    }
}
