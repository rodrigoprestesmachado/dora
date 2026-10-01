package dev.rpmhub.domain.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link PersonNameValidator}.
 *
 * @author Rodrigo Prestes Machado
 */
class PersonNameValidatorTest {

    @ParameterizedTest
    @CsvSource({
            "Maria Souza, MARIA SOUZA",
            "  joão   da silva , JOÃO DA SILVA",
            "Maria d Silva, MARIA D SILVA",
    })
    void normalizeAcceptsNamesWithAtLeastTwoSubstantialWords(String raw, String expected) {
        assertEquals(expected, PersonNameValidator.normalize(raw));
        assertTrue(PersonNameValidator.isValid(raw));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   ", "Maria", "M Souza", "A B" })
    void normalizeRejectsNamesWithoutTwoSubstantialWords(String raw) {
        assertNull(PersonNameValidator.normalize(raw));
        assertFalse(PersonNameValidator.isValid(raw));
    }
}
