/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */
package dev.rpmhub.adapter.out.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * LangChain4j AI service that answers legal questions for a law office.
 *
 * @author Rodrigo Prestes Machado
 */
@RegisterAiService(tools = DoraTools.class)
@ApplicationScoped
public interface DoraAgent {

    /**
     * Streams a chat response grounded in RAG-retrieved context.
     *
     * @param memoryId stable identifier (phone number) used to isolate conversational memory per user
     * @param context  relevant passages retrieved from the vector store (may be empty)
     * @param prompt   the user question
     * @return a multi that emits the response chunks
     */
    @SystemMessage(fromResource = "prompts/dora-system.txt")
    @UserMessage("Contexto: {context}\n\nPergunta: {prompt}")
    Multi<String> answer(@MemoryId String memoryId, String context, String prompt);

}
