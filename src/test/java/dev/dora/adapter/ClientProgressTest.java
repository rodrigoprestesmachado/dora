package dev.dora.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dora.domain.port.out.ClientNoticePort;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ClientProgress}.
 *
 * @author Rodrigo Prestes Machado
 */
class ClientProgressTest {

    @AfterEach
    void clearPhone() {
        ConversationPhone.clear();
    }

    @Test
    void start_sendsAnUpdateOnEachInterval_andStopsWhenClosed() throws Exception {
        List<String> notices = new ArrayList<>();
        CountDownLatch twoUpdates = new CountDownLatch(2);
        ClientNoticePort port = text -> {
            assertEquals("5551999999999", ConversationPhone.current());
            synchronized (notices) {
                notices.add(text);
            }
            twoUpdates.countDown();
        };

        try (ClientProgress progress = ClientProgress.start("5551999999999", port, 40,
                TimeUnit.MILLISECONDS)) {
            assertTrue(twoUpdates.await(2, TimeUnit.SECONDS));
            progress.close();
            int sent = notices.size();
            Thread.sleep(150);
            assertTrue(notices.size() <= sent + 1);
            assertTrue(notices.get(0).contains("TJRS"));
            assertTrue(notices.get(1).contains("tribunal") || notices.get(1).contains("TJRS"));
        }
    }

    @Test
    void everyTenSeconds_staysQuiet_whenThereIsNoPhone() throws Exception {
        List<String> notices = new ArrayList<>();
        try (ClientProgress progress = ClientProgress.everyTenSeconds("  ", notices::add)) {
            Thread.sleep(50);
            progress.close();
        }
        assertTrue(notices.isEmpty());
    }
}
