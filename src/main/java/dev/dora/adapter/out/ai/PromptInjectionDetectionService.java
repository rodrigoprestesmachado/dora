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

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * AI service that scores whether a client message is a prompt injection.
 *
 * <p>It has no tools and no chat memory, so a malicious message cannot call
 * Dora's functions or ride on a previous conversation.
 *
 * @author Rodrigo Prestes Machado
 */
@RegisterAiService(
        chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
@ApplicationScoped
public interface PromptInjectionDetectionService {

    /**
     * Scores how likely the client message is a prompt injection.
     *
     * @param userQuery the client message, without the RAG context
     * @return a decimal between 0.0 and 1.0; the model may add surrounding text
     */
    @SystemMessage(fromResource = "prompts/prompt-injection-system.txt")
    @UserMessage(fromResource = "prompts/prompt-injection-user.txt")
    String score(String userQuery);
}
