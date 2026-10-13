/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */

package dev.dora.domain.port.in;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

/**
 * Driving port for chatting with Dora.
 *
 * @author Rodrigo Prestes Machado
 */
public interface ChatUseCase {

    /**
     * Accepts a user message, assigns it to a chat session, and streams a reply.
     *
     * @param phoneNumber phone number that identifies the user
     * @param message text content sent by the user
     * @return a multi that emits the assistant response as plain text chunks
     */
    Multi<String> chat(String phoneNumber, String message);

    /**
     * Convenience variant of {@link #chat(String, String)} for callers that need the
     * complete assistant reply as a single value instead of a stream (e.g. WhatsApp,
     * which cannot deliver partial/streamed messages). Reuses the same streaming
     * pipeline, so RAG retrieval, persistence and memory behave identically.
     *
     * @param phoneNumber phone number that identifies the user
     * @param message text content sent by the user
     * @return a uni that emits the full assistant response once streaming completes
     */
    default Uni<String> chatSync(String phoneNumber, String message) {
        return chat(phoneNumber, message)
                .collect().in(StringBuilder::new, StringBuilder::append)
                .map(StringBuilder::toString);
    }

}
