package dev.dora.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ClientNoticeDispatcher}.
 *
 * @author Rodrigo Prestes Machado
 */
class ClientNoticeDispatcherTest {

    private final ClientNoticeDispatcher dispatcher = new ClientNoticeDispatcher();

    @AfterEach
    void clear() {
        ConversationPhone.clear();
    }

    @Test
    void notify_deliversThroughTheInterfaceThatOpenedTheConversation() {
        List<String> web = new ArrayList<>();
        List<String> whatsapp = new ArrayList<>();
        try (ClientSession.Registration webSession = ClientSession.open("5551999999999", web::add);
                ClientSession.Registration whatsappSession = ClientSession.open("5551888888888",
                        whatsapp::add)) {
            ConversationPhone.set("5551999999999");

            dispatcher.notify("Ainda estou consultando o TJRS.");

            assertEquals(List.of("Ainda estou consultando o TJRS."), web);
            assertTrue(whatsapp.isEmpty());
        }
    }

    @Test
    void notify_stopsWhenTheConversationCloses() {
        List<String> sent = new ArrayList<>();
        ClientSession.Registration session = ClientSession.open("5551999999999", sent::add);
        ConversationPhone.set("5551999999999");

        dispatcher.notify("primeira");
        session.close();
        dispatcher.notify("segunda");

        assertEquals(List.of("primeira"), sent);
    }
}
