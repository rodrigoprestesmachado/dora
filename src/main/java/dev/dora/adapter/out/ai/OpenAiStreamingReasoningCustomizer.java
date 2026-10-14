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

import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import io.quarkiverse.langchain4j.ModelBuilderCustomizer;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Copies {@code quarkus.langchain4j.openai.chat-model.reasoning-effort} onto the streaming
 * chat client.
 *
 * <p>Quarkus applies that property to the synchronous client and to the Responses API client.
 * The streaming Chat Completions client, which {@link DoraAgent} uses, ignores it. gpt-6-luna
 * then defaults to medium effort and rejects function tools on {@code /v1/chat/completions}.
 *
 * @author Rodrigo Prestes Machado
 */
@ApplicationScoped
public class OpenAiStreamingReasoningCustomizer
        implements ModelBuilderCustomizer<OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder> {

    private final String reasoningEffort;

    /**
     * @param reasoningEffort value of {@code quarkus.langchain4j.openai.chat-model.reasoning-effort};
     *                        empty when the property is unset (tests stay on Ollama)
     */
    public OpenAiStreamingReasoningCustomizer(
            @ConfigProperty(name = "quarkus.langchain4j.openai.chat-model.reasoning-effort")
            Optional<String> reasoningEffort) {
        this.reasoningEffort = reasoningEffort.map(String::trim).filter(value -> !value.isEmpty()).orElse(null);
    }

    @Override
    public void customize(OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder) {
        if (reasoningEffort != null) {
            builder.reasoningEffort(reasoningEffort);
        }
    }
}
