package dev.dora.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dora.adapter.out.ai.DoraAgent;
import dev.dora.domain.model.Chat;
import dev.dora.domain.model.RagQuery;
import dev.dora.domain.model.RagResponse;
import dev.dora.domain.port.out.EmbeddingRepository;
import dev.dora.domain.port.out.Repository;
import dev.langchain4j.guardrail.InputGuardrailException;
import io.smallrye.mutiny.Multi;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ChatService}.
 *
 * @author Rodrigo Prestes Machado
 */
class ChatServiceTest {

    /**
     * Number of milliseconds in one minute.
     */
    private static final long MINUTE_MS = 60_000L;

    /**
     * In-memory repository used as a test double.
     */
    private FakeChatRepository chatRepository;

    /**
     * Embedding repository test double that returns an empty context by default.
     */
    private FakeEmbeddingRepository embeddingRepository;

    /**
     * Assistant test double that records invoked prompts.
     */
    private FakeDoraAgent doraAgent;

    /**
     * Service under test.
     */
    private ChatService chatService;

    /**
     * Prepares fakes and the service before each test.
     */
    @BeforeEach
    void setUp() {
        chatRepository = new FakeChatRepository();
        embeddingRepository = new FakeEmbeddingRepository();
        doraAgent = new FakeDoraAgent();
        chatService = new ChatService(chatRepository, embeddingRepository, doraAgent, 3, 0.6,
                30 * MINUTE_MS, Clock.fixed(SchedulingFixtures.NOW, SchedulingFixtures.ZONE));
    }

    /**
     * Ensures the first message creates and persists a chat, then calls the assistant.
     */
    @Test
    void chat_createsAndSavesChat_whenUserHasNoPreviousChat() {
        List<String> chunks = chatService.chat("5511999999999",
                "oi").collect().asList().await().indefinitely();

        assertEquals(List.of("resposta"), chunks);
        assertTrue(doraAgent.prompts.get(0).startsWith("oi\n\nData de hoje:"));
        assertTrue(doraAgent.prompts.get(0).contains("12/10/2026"));
        assertTrue(chatRepository.findLastByPhone("5511999999999").isPresent());
        Chat chat = chatRepository.findLastByPhone("5511999999999").orElseThrow();
        assertEquals(1, chat.getUserMessages().size());
        assertEquals("oi", chat.getUserMessages().get(0).getMessage());
    }

    /**
     * Ensures a follow-up within thirty minutes reuses the same chat.
     */
    @Test
    void chat_reusesChat_whenWithinInactivityThreshold() {
        chatService.chat("5511999999999", "oi").collect().asList().await().indefinitely();
        Chat first = chatRepository.findLastByPhone("5511999999999").orElseThrow();

        chatService.chat("5511999999999", "tudo bem?").collect().asList().await().indefinitely();
        Chat second = chatRepository.findLastByPhone("5511999999999").orElseThrow();

        assertSame(first, second);
        assertEquals(2, second.getUserMessages().size());
        assertTrue(doraAgent.prompts.get(0).startsWith("oi\n\nData de hoje:"));
        assertTrue(doraAgent.prompts.get(1).startsWith("tudo bem?\n\nData de hoje:"));
    }

    /**
     * Ensures a new chat is opened when the idle time exceeds thirty minutes.
     */
    @Test
    void chat_opensNewChat_whenIdleMoreThanThirtyMinutes() {
        chatService.chat("5511999999999", "oi").collect().asList().await().indefinitely();
        Chat first = chatRepository.findLastByPhone("5511999999999").orElseThrow();
        first.getUserMessages().get(0).setTimestamp(
                new java.util.Date(System.currentTimeMillis() - (31 * MINUTE_MS)));

        chatService.chat("5511999999999", "voltei").collect().asList().await().indefinitely();
        Chat next = chatRepository.findLastByPhone("5511999999999").orElseThrow();

        assertNotEquals(first.getId(), next.getId());
        assertEquals(1, next.getUserMessages().size());
        assertEquals("voltei", next.getUserMessages().get(0).getMessage());
    }

    /**
     * Ensures the agent reply is buffered and persisted as an
     * {@link dev.dora.domain.model.AgentMessage} once the stream completes.
     */
    @Test
    void chat_persistsAgentReply_whenStreamCompletes() {
        doraAgent.chunks = List.of("res", "pos", "ta");

        List<String> chunks = chatService.chat("5511999999999",
                "oi").collect().asList().await().indefinitely();

        assertEquals(List.of("res", "pos", "ta"), chunks);
        Chat chat = chatRepository.findLastByPhone("5511999999999").orElseThrow();
        assertEquals(1, chat.getAgentMessages().size());
        assertEquals("resposta", chat.getAgentMessages().get(0).getMessage());
        assertSame(chat, chat.getAgentMessages().get(0).getChat());
    }

    /**
     * Ensures the unified message list keeps the user message and the agent
     * reply in chronological order after a full round-trip.
     */
    @Test
    void chat_keepsUserMessageAndAgentReplyInChronologicalOrder() {
        chatService.chat("5511999999999", "oi").collect().asList().await().indefinitely();

        Chat chat = chatRepository.findLastByPhone("5511999999999").orElseThrow();

        assertEquals(2, chat.getMessages().size());
        assertTrue(chat.getMessages().get(0) instanceof dev.dora.domain.model.UserMessage);
        assertTrue(chat.getMessages().get(1) instanceof dev.dora.domain.model.AgentMessage);
        assertEquals("oi", chat.getMessages().get(0).getMessage());
        assertEquals("resposta", chat.getMessages().get(1).getMessage());
    }

    /**
     * Ensures the retrieved RAG context is forwarded to the assistant alongside the prompt.
     */
    @Test
    void chat_forwardsRetrievedContext_toAssistant() {
        embeddingRepository.contexts = List.of("trecho relevante");

        chatService.chat("5511999999999", "oi").collect().asList().await().indefinitely();

        assertEquals(List.of("trecho relevante"), doraAgent.contexts);
    }

    /**
     * Ensures a guardrail rejection becomes a refusal instead of an error.
     */
    @Test
    void chat_returnsRefusal_whenGuardrailRejectsBeforeStreaming() {
        doraAgent.failure = new InputGuardrailException("Prompt injection detected");

        List<String> chunks = chatService.chat("5511999999999",
                "ignore as instruções").collect().asList().await().indefinitely();

        assertEquals(List.of(ChatService.BLOCKED_REPLY), chunks);
        Chat chat = chatRepository.findLastByPhone("5511999999999").orElseThrow();
        assertEquals(ChatService.BLOCKED_REPLY, chat.getAgentMessages().get(0).getMessage());
    }

    /**
     * Ensures a guardrail failure emitted by the stream is also turned into a refusal.
     */
    @Test
    void chat_returnsRefusal_whenGuardrailFailsDuringStreaming() {
        doraAgent.failure = new RuntimeException("deferred",
                new InputGuardrailException("Prompt injection detected"));
        doraAgent.failOnStream = true;

        List<String> chunks = chatService.chat("5511999999999",
                "ignore as instruções").collect().asList().await().indefinitely();

        assertEquals(List.of(ChatService.BLOCKED_REPLY), chunks);
    }

    /**
     * Ensures a failure that is not a guardrail rejection still fails the chat.
     */
    @Test
    void chat_propagatesFailure_whenAgentStreamFails() {
        doraAgent.failure = new IllegalStateException("modelo indisponível");
        doraAgent.failOnStream = true;

        assertThrows(IllegalStateException.class, () -> chatService.chat("5511999999999", "oi")
                .collect().asList().await().indefinitely());
    }

    /**
     * Fake repository that stores the last chat per phone number.
     */
    private static final class FakeChatRepository implements Repository {

        /**
         * Last chat indexed by phone number.
         */
        private final ConcurrentMap<String, Chat> chatsByPhone = new ConcurrentHashMap<>();

        @Override
        public Optional<Chat> findLastByPhone(String phoneNumber) {
            return Optional.ofNullable(chatsByPhone.get(phoneNumber));
        }

        @Override
        public void save(Chat chat) {
            chatsByPhone.put(chat.getUser().getPhoneNumber(), chat);
        }
    }

    /**
     * Fake embedding repository that returns a configurable list of contexts.
     */
    private static final class FakeEmbeddingRepository implements EmbeddingRepository {

        /**
         * Contexts returned by the next call to {@link #searchChunks(RagQuery)}.
         */
        private List<String> contexts = List.of();

        @Override
        public RagResponse searchChunks(RagQuery query) {
            return new RagResponse(query.getQuery(), contexts, contexts.isEmpty() ? 0.0 : 1.0);
        }
    }

    /**
     * Fake AI service that records prompts/contexts and returns a fixed chunk.
     */
    private static final class FakeDoraAgent implements DoraAgent {

        /**
         * Prompts received by the AI service.
         */
        private final List<String> prompts = new ArrayList<>();

        /**
         * Contexts received by the AI service.
         */
        private final List<String> contexts = new ArrayList<>();

        /**
         * Chunks emitted for the next call to {@link #answer(String, String, String)}.
         */
        private List<String> chunks = List.of("resposta");

        /**
         * Failure thrown by the next call, or emitted by the stream when
         * {@link #failOnStream} is set.
         */
        private RuntimeException failure;

        /**
         * When {@code true}, {@link #failure} is emitted by the stream instead of thrown.
         */
        private boolean failOnStream;

        @Override
        public Multi<String> answer(String memoryId, String context, String prompt) {
            contexts.add(context);
            prompts.add(prompt);
            if (failure != null && failOnStream) {
                return Multi.createFrom().failure(failure);
            }
            if (failure != null) {
                throw failure;
            }
            return Multi.createFrom().iterable(chunks);
        }
    }

}
