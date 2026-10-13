package dev.dora.adapter.in.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dora.adapter.ClientNoticeDispatcher;
import dev.dora.adapter.ConversationPhone;
import io.smallrye.mutiny.Multi;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link WebChatProgress}.
 *
 * @author Rodrigo Prestes Machado
 */
class WebChatProgressTest {

    @AfterEach
    void clearPhone() {
        ConversationPhone.clear();
    }

    @Test
    void interleave_framesTheNoticeAndThenTheAnswer() {
        List<String> items = WebChatProgress.interleave("5551999999999",
                Multi.createFrom().emitter(emitter -> {
                    ConversationPhone.set("5551999999999");
                    try {
                        new ClientNoticeDispatcher().notify("Ainda estou consultando o TJRS.");
                        emitter.emit("Olá");
                        emitter.complete();
                    } finally {
                        ConversationPhone.clear();
                    }
                })).collect().asList().await().atMost(Duration.ofSeconds(5));

        assertEquals(WebChatProgress.MARK + "Ainda estou consultando o TJRS."
                + WebChatProgress.MARK, items.get(0));
        assertEquals("Olá", items.get(1));
        assertEquals(2, items.size());
    }

    @Test
    void interleave_ignoresANoticeAfterTheAnswerCompletes() {
        List<String> items = WebChatProgress.interleave("5551999999999",
                Multi.createFrom().item("pronta"))
                .collect().asList().await().atMost(Duration.ofSeconds(5));

        ConversationPhone.set("5551999999999");
        new ClientNoticeDispatcher().notify("tarde demais");

        assertEquals(List.of("pronta"), items);
        assertTrue(items.stream().noneMatch(item -> item.contains("tarde demais")));
    }
}
