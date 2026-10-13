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

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.InputGuardrails;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * LangChain4j AI service that answers legal questions for a law office.
 * Tools are supplied by {@link DoraToolCatalog}.
 *
 * @author Rodrigo Prestes Machado
 */
@RegisterAiService(toolProviderSupplier = DoraToolCatalog.class)
@ApplicationScoped
public interface DoraAgent {

    /**
     * Separator that {@code ChatService} places between the client text and the date note it
     * appends. The date note is a system instruction, not client input, so the injection guard
     * must not score it.
     */
    String TODAY_MARKER = "\n\nData de hoje: ";

    /**
     * Streams a chat response grounded in RAG-retrieved context.
     * {@link PromptInjectionGuard} runs before the model and its tools.
     *
     * @param memoryId stable identifier (phone number) used to isolate conversational memory per
     *     user
     * @param context  relevant passages retrieved from the vector store (may be empty)
     * @param prompt   the client message, which may be a question or a short reply
     * @return a multi that emits the response chunks
     */
    @SystemMessage(fromResource = "prompts/dora-system.txt")
    @UserMessage("Contexto: {context}\n\nMensagem do cliente: {prompt}")
    @InputGuardrails(PromptInjectionGuard.class)
    Multi<String> answer(@MemoryId String memoryId, String context, String prompt);

}
