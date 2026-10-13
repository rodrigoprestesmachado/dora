package dev.dora.adapter.out.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Parser checks for {@link DoraToolTexts}.
 *
 * @author Rodrigo Prestes Machado
 */
class DoraToolTextsTest {

    @Test
    void parseJoinsWrappedLinesAndSkipsComments() {
        Map<String, String> entries = DoraToolTexts.parse("""
                # comentario

                [cancel_meeting]
                Cancela uma reunião.
                Segunda linha.

                [cancel_meeting.eventId]
                ID da reunião.
                """);

        assertEquals("Cancela uma reunião. Segunda linha.", entries.get("cancel_meeting"));
        assertEquals("ID da reunião.", entries.get("cancel_meeting.eventId"));
    }

    @Test
    void parseRejectsADuplicateBlock() {
        assertThrows(IllegalStateException.class, () -> DoraToolTexts.parse("""
                [cancel_meeting]
                um
                [cancel_meeting]
                dois
                """));
    }

    @Test
    void parseRejectsTextOutsideABlock() {
        assertThrows(IllegalStateException.class, () -> DoraToolTexts.parse("solto\n"));
    }

    @Test
    void parseRejectsAnEmptyBlock() {
        assertThrows(IllegalStateException.class, () -> DoraToolTexts.parse("""
                [cancel_meeting]

                [outro]
                texto
                """));
    }
}
