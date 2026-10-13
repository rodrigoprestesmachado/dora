/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */

package dev.dora.application;

import dev.dora.adapter.out.ai.DoraAgent;
import dev.dora.domain.model.AgentMessage;
import dev.dora.domain.model.Chat;
import dev.dora.domain.model.RagQuery;
import dev.dora.domain.model.RagResponse;
import dev.dora.domain.model.User;
import dev.dora.domain.model.UserMessage;
import dev.dora.domain.port.in.ChatUseCase;
import dev.dora.domain.port.out.EmbeddingRepository;
import dev.dora.domain.port.out.Repository;
import dev.langchain4j.guardrail.InputGuardrailException;
import io.smallrye.mutiny.Multi;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;

/**
 * Application service that orchestrates chat acceptance and RAG-grounded
 * assistant replies.
 *
 * <p>This class is deliberately framework-agnostic (plain Java) so it can be
 * unit tested without a CDI container. Its lifecycle and wiring are handled by
 * {@code dev.dora.adapter.config.ApplicationBeans}.
 *
 * @author Rodrigo Prestes Machado
 */
public class ChatService implements ChatUseCase {

    /** Fallback context string used when no relevant chunk is found. */
    private static final String DEFAULT_CONTEXT = "";

    /**
     * Reply returned when the input guardrail rejects the client message.
     */
    static final String BLOCKED_REPLY = "Desculpe, não posso atender essa mensagem. "
            + "Posso ajudar com informações do escritório, andamento de processo ou agendamento.";

    /**
     * Repository used to load and store the last chat per user.
     */
    private final Repository chatRepository;

    /** Repository used to search embedding chunks relevant to the message. */
    private final EmbeddingRepository embeddingRepository;

    /** AI service used to generate a streaming reply grounded in retrieved context. */
    private final DoraAgent doraAgent;

    /** Number of context chunks retrieved per message ({@code rag.max-results}). */
    private final int maxResults;

    /**
     * Minimum similarity score required for a retrieved chunk to be used as context
     * ({@code rag.min-score}).
     */
    private final double minScore;

    /** Maximum idle time, in milliseconds,
            before a new chat session starts ({@code chat.inactivity-threshold-minutes}). */
    private final long inactivityThresholdMs;

    /** Clock in the office time zone, used to tell the model which day it is. */
    private final Clock clock;

    /**
     * Creates the chat service with its driven ports.
     *
     * @param chatRepository        port used to persist chats
     * @param embeddingRepository   port for vector-similarity search
     * @param doraAgent             AI service used to generate contextual replies
     * @param maxResults            number of context chunks retrieved per message
     * @param minScore              minimum similarity score required for a retrieved chunk to be
     *     used as context
     * @param inactivityThresholdMs maximum idle time, in milliseconds, before a new chat
     *     session starts
     * @param clock                 clock in the office time zone
     */
    public ChatService(Repository chatRepository, EmbeddingRepository embeddingRepository,
            DoraAgent doraAgent, int maxResults, double minScore, long inactivityThresholdMs,
            Clock clock) {
        this.chatRepository = chatRepository;
        this.embeddingRepository = embeddingRepository;
        this.doraAgent = doraAgent;
        this.maxResults = maxResults;
        this.minScore = minScore;
        this.inactivityThresholdMs = inactivityThresholdMs;
        this.clock = clock;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Multi<String> chat(String phoneNumber, String message) {
        User user = new User();
        user.setPhoneNumber(phoneNumber);

        UserMessage userMessage = new UserMessage();
        userMessage.setUser(user);
        userMessage.setMessage(message);
        userMessage.setTimestamp(new Date());

        Chat lastChat = chatRepository.findLastByPhone(phoneNumber).orElse(null);
        Chat chat = Chat.accept(lastChat, userMessage, inactivityThresholdMs);
        chatRepository.save(chat);

        RagQuery query = new RagQuery(message, maxResults, minScore);
        RagResponse ragResponse = embeddingRepository.searchChunks(query);
        String context = ragResponse.getContexts().isEmpty()
                ? DEFAULT_CONTEXT : String.join("\n\n", ragResponse.getContexts());

        StringBuilder buffer = new StringBuilder();

        return agentReply(phoneNumber, context, withToday(message))
                .invoke(buffer::append)
                .onCompletion().invoke(() -> {
                    AgentMessage agentMessage = new AgentMessage();
                    agentMessage.setMessage(buffer.toString());
                    agentMessage.setTimestamp(new Date());
                    chat.addMessage(agentMessage);
                    chatRepository.save(chat);
                });
    }

    private Multi<String> agentReply(String phoneNumber, String context, String prompt) {
        try {
            return doraAgent.answer(phoneNumber, context, prompt)
                    .onFailure(ChatService::causedByInputGuardrail)
                    .recoverWithItem(BLOCKED_REPLY);
        } catch (InputGuardrailException exception) {
            return Multi.createFrom().item(BLOCKED_REPLY);
        }
    }

    private static boolean causedByInputGuardrail(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof InputGuardrailException) {
                return true;
            }
        }
        return false;
    }

    private String withToday(String message) {
        String today = LocalDate.now(clock).format(DateTimeFormatter.ofPattern(
                "EEEE, dd/MM/yyyy", Locale.of("pt", "BR")));
        return message + DoraAgent.TODAY_MARKER + today
                + ". Use esta data para 'este mês' e 'última semana do mês', neste ano.";
    }
}
