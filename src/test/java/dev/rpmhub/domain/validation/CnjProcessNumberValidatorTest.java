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
 * Unit tests for {@link CnjProcessNumberValidator}.
 *
 * @author Rodrigo Prestes Machado
 */
class CnjProcessNumberValidatorTest {

    @ParameterizedTest
    @CsvSource({
            "5033013-66.2026.8.21.0022,   5033013-66.2026.8.21.0022", // already canonical
            "50330136620268210022,        5033013-66.2026.8.21.0022", // digits only
            " 5033013-66.2026.8.21.0022 , 5033013-66.2026.8.21.0022", // surrounding whitespace
            "5033013 66 2026 8 21 0022,   5033013-66.2026.8.21.0022", // stray separators instead of -/.
    })
    void normalizeAcceptsValidCnjNumbers(String raw, String expected) {
        assertEquals(expected, CnjProcessNumberValidator.normalize(raw));
        assertTrue(CnjProcessNumberValidator.isValid(raw));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "abc",                             // not a number at all
            "5033013-66.2026.8.21.002",         // one digit short (19 digits)
            "5033013-66.2026.8.21.00222",       // one digit too many (21 digits)
            "503301366202682100220",            // 21 digits, no punctuation
    })
    void normalizeRejectsInvalidNumbers(String raw) {
        assertNull(CnjProcessNumberValidator.normalize(raw));
        assertFalse(CnjProcessNumberValidator.isValid(raw));
    }
}
