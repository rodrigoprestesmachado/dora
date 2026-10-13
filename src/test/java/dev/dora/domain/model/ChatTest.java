package dev.dora.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Chat} inactivity and accept rules.
 *
 * <p>Covers creation of a first chat, reuse within thirty minutes,
 * opening a new chat after the threshold, and {@link Chat#isExpiredAt(Date)}
 * boundary checks.</p>
 *
 * @author Rodrigo Prestes Machado
 */
class ChatTest {

    /**
     * Number of milliseconds in one minute.
     */
    private static final long MINUTE_MS = 60_000L;

    /**
     * Inactivity threshold used by these tests, equivalent to the
     * {@code chat.inactivity-threshold-minutes} default (30 minutes). The
     * value lives only in this test class — {@link Chat} has no default of
     * its own and always requires an explicit threshold from the caller.
     */
    private static final long INACTIVITY_THRESHOLD_MS = 30 * MINUTE_MS;

    /**
     * Fixed base instant used to build deterministic message timestamps.
     */
    private static final long BASE_TIME_MS = 1_700_000_000_000L;

    /**
     * Ensures {@link Chat#accept(Chat, UserMessage, long)} starts a new chat
     * when there is no previous chat for the user.
     */
    @Test
    void accept_createsNewChat_whenLastChatIsNull() {
        User user = user("5511999999999");
        UserMessage first = message(user, "oi", at(0));

        Chat chat = Chat.accept(null, first, INACTIVITY_THRESHOLD_MS);

        assertNotNull(chat.getId());
        assertSame(user, chat.getUser());
        assertEquals(1, chat.getUserMessages().size());
        assertSame(chat, first.getChat());
    }

    /**
     * Ensures the same chat is reused when the idle time is below thirty
     * minutes.
     */
    @Test
    void accept_reusesChat_whenWithinThirtyMinutes() {
        User user = user("5511999999999");
        UserMessage first = message(user, "oi", at(0));
        Chat chat = Chat.accept(null, first, INACTIVITY_THRESHOLD_MS);

        UserMessage second = message(user, "tudo bem?", at(29 * MINUTE_MS));
        Chat same = Chat.accept(chat, second, INACTIVITY_THRESHOLD_MS);

        assertEquals(chat.getId(), same.getId());
        assertEquals(2, same.getUserMessages().size());
        assertSame(chat, second.getChat());
    }

    /**
     * Ensures the same chat is reused when the idle time is exactly thirty
     * minutes, because expiration uses a strict greater-than comparison.
     */
    @Test
    void accept_reusesChat_whenIdleEqualsThirtyMinutes() {
        User user = user("5511999999999");
        Chat chat = Chat.accept(null, message(user, "oi", at(0)), INACTIVITY_THRESHOLD_MS);

        Chat same = Chat.accept(chat, message(user, "ainda aqui",
                at(30 * MINUTE_MS)), INACTIVITY_THRESHOLD_MS);

        assertEquals(chat.getId(), same.getId());
        assertEquals(2, same.getUserMessages().size());
    }

    /**
     * Ensures a new chat is opened when the idle time exceeds thirty minutes
     * and that the previous chat keeps only its original messages.
     */
    @Test
    void accept_opensNewChat_whenIdleMoreThanThirtyMinutes() {
        User user = user("5511999999999");
        Chat chat = Chat.accept(null, message(user, "oi", at(0)), INACTIVITY_THRESHOLD_MS);

        UserMessage later = message(user, "voltei", at(31 * MINUTE_MS));
        Chat next = Chat.accept(chat, later, INACTIVITY_THRESHOLD_MS);

        assertNotEquals(chat.getId(), next.getId());
        assertEquals(1, next.getUserMessages().size());
        assertSame(next, later.getChat());
        assertEquals(1, chat.getUserMessages().size());
    }

    /**
     * Ensures {@link Chat#isExpiredAt(Date, long)} returns {@code false} for
     * idle times up to and including thirty minutes.
     */
    @Test
    void isExpiredAt_returnsFalse_whenWithinThreshold() {
        User user = user("5511999999999");
        Chat chat = Chat.accept(null, message(user, "oi", at(0)), INACTIVITY_THRESHOLD_MS);

        assertFalse(chat.isExpiredAt(at(29 * MINUTE_MS), INACTIVITY_THRESHOLD_MS));
        assertFalse(chat.isExpiredAt(at(30 * MINUTE_MS), INACTIVITY_THRESHOLD_MS));
    }

    /**
     * Ensures {@link Chat#isExpiredAt(Date, long)} returns {@code true} when
     * the idle time is greater than thirty minutes.
     */
    @Test
    void isExpiredAt_returnsTrue_whenBeyondThreshold() {
        User user = user("5511999999999");
        Chat chat = Chat.accept(null, message(user, "oi", at(0)), INACTIVITY_THRESHOLD_MS);

        assertTrue(chat.isExpiredAt(at(31 * MINUTE_MS), INACTIVITY_THRESHOLD_MS));
    }

    /**
     * Ensures {@link Chat#addMessage(Message)} links an agent reply back to
     * the chat and exposes it through {@link Chat#getAgentMessages()}.
     */
    @Test
    void addMessage_linksAgentReplyToChat() {
        User user = user("5511999999999");
        Chat chat = Chat.accept(null, message(user, "oi", at(0)), INACTIVITY_THRESHOLD_MS);

        AgentMessage reply = new AgentMessage();
        reply.setMessage("resposta");
        reply.setTimestamp(at(1_000L));
        chat.addMessage(reply);

        assertEquals(1, chat.getAgentMessages().size());
        assertSame(chat, reply.getChat());
        assertEquals("resposta", chat.getAgentMessages().get(0).getMessage());
    }

    /**
     * Ensures {@link Chat#getMessages()} keeps the chronological order of
     * user messages and agent replies in a single unified list.
     */
    @Test
    void getMessages_returnsUserAndAgentMessagesInChronologicalOrder() {
        User user = user("5511999999999");
        UserMessage userMessage = message(user, "oi", at(0));
        Chat chat = Chat.accept(null, userMessage, INACTIVITY_THRESHOLD_MS);

        AgentMessage reply = new AgentMessage();
        reply.setMessage("resposta");
        reply.setTimestamp(at(1_000L));
        chat.addMessage(reply);

        assertEquals(2, chat.getMessages().size());
        assertSame(userMessage, chat.getMessages().get(0));
        assertSame(reply, chat.getMessages().get(1));
    }

    /**
     * Builds a user identified by the given phone number.
     *
     * @param phoneNumber the phone number to assign
     * @return a configured user
     */
    private static User user(String phoneNumber) {
        User user = new User();
        user.setPhoneNumber(phoneNumber);
        return user;
    }

    /**
     * Builds a user message with the given content and timestamp.
     *
     * @param user the message author
     * @param text the message content
     * @param timestamp the message timestamp
     * @return a configured user message
     */
    private static UserMessage message(User user, String text, Date timestamp) {
        UserMessage message = new UserMessage();
        message.setUser(user);
        message.setMessage(text);
        message.setTimestamp(timestamp);
        return message;
    }

    /**
     * Returns a date offset from the fixed base instant.
     *
     * @param offsetMs milliseconds to add to the base time
     * @return the resulting date
     */
    private static Date at(long offsetMs) {
        return new Date(BASE_TIME_MS + offsetMs);
    }

}
