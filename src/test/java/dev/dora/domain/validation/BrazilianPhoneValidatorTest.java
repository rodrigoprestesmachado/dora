package dev.dora.domain.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link BrazilianPhoneValidator}.
 *
 * @author Rodrigo Prestes Machado
 */
class BrazilianPhoneValidatorTest {

    @ParameterizedTest
    @CsvSource({
        "11987654321,             5511987654321", // mobile, digits only, no country code
        "5511987654321,           5511987654321", // mobile, digits only, with country code
        "+55 11 98765-4321,       5511987654321", // mobile, fully formatted with +55
        "(11) 98765-4321,         5511987654321", // mobile, fully formatted without country code
        "1123456789,              551123456789",  // landline, digits only, no country code
        "5511 2345-6789,          551123456789",  // landline, formatted with country code
        "(21) 3456-7890,          552134567890",  // landline, another valid DDD
        "5599987654321,           5599987654321", // mobile, DDD 99 (highest valid)
    })
    void normalizeAcceptsValidBrazilianNumbers(String raw, String expected) {
        assertEquals(expected, BrazilianPhoneValidator.normalize(raw));
        assertTrue(BrazilianPhoneValidator.isValid(raw));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "abc",                  // not a phone number
        "123",                  // too short
        "551198765432",         // country code + DDD + 8 digits (mobile must have 9)
        "5500987654321",        // invalid DDD (00)
        "5510987654321",        // invalid DDD (10 does not exist)
        "551187654321X",        // stray non-digit character embedded in digits
        "5511187654321",        // 11 digits after removing +55, but too long overall
        "551112345678",         // landline with mobile-looking prefix but wrong length
        "5511012345678",        // subscriber starting with 0 (invalid for landline/mobile)
        "551111234567",         // subscriber starting with 1 (invalid)
    })
    void normalizeRejectsInvalidNumbers(String raw) {
        assertNull(BrazilianPhoneValidator.normalize(raw));
        assertFalse(BrazilianPhoneValidator.isValid(raw));
    }
}
