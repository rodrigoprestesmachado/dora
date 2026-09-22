package dev.rpmhub.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.rpmhub.domain.model.ProcessLookupResult;
import dev.rpmhub.domain.port.out.ProcessLookupException;
import dev.rpmhub.domain.port.out.ProcessLookupPort;

/**
 * Unit tests for {@link ProcessLookupService}.
 *
 * @author Rodrigo Prestes Machado
 */
class ProcessLookupServiceTest {

    /** Canonical example number used across tests, taken from the TJRS search page docs. */
    private static final String VALID_NUMBER = "5033013-66.2026.8.21.0022";

    private FakeProcessLookupPort processLookupPort;
    private ProcessLookupService processLookupService;

    @BeforeEach
    void setUp() {
        processLookupPort = new FakeProcessLookupPort();
        processLookupService = new ProcessLookupService(processLookupPort);
    }

    @Test
    void lookup_rejectsInvalidProcessNumber_withoutCallingThePort() {
        String result = processLookupService.lookup("not-a-number");

        assertTrue(result.contains("não é válido"));
        assertEquals(0, processLookupPort.lookupCalls);
    }

    @Test
    void lookup_normalizesNumber_beforeCallingThePort() {
        processLookupPort.result = Optional.of(new ProcessLookupResult(VALID_NUMBER, "# ok", "https://x"));

        processLookupService.lookup("50330136620268210022");

        assertEquals(VALID_NUMBER, processLookupPort.lastQuery);
    }

    @Test
    void lookup_returnsMarkdown_whenPortFindsTheProcess() {
        processLookupPort.result = Optional.of(new ProcessLookupResult(VALID_NUMBER, "# Andamento", "https://x"));

        String result = processLookupService.lookup(VALID_NUMBER);

        assertEquals("# Andamento", result);
    }

    @Test
    void lookup_returnsClearMessage_whenProcessIsNotFound() {
        processLookupPort.result = Optional.empty();

        String result = processLookupService.lookup(VALID_NUMBER);

        assertTrue(result.contains("Não foi possível localizar"));
        assertTrue(result.contains(VALID_NUMBER));
    }

    @Test
    void lookup_returnsClearMessage_whenPortThrowsProcessLookupException() {
        processLookupPort.exceptionToThrow = new ProcessLookupException("timeout");

        String result = processLookupService.lookup(VALID_NUMBER);

        assertTrue(result.contains("Não foi possível consultar"));
    }

    @Test
    void lookup_returnsClearMessage_whenPortThrowsUnexpectedException() {
        processLookupPort.exceptionToThrow = new RuntimeException("boom");

        String result = processLookupService.lookup(VALID_NUMBER);

        assertTrue(result.contains("erro inesperado"));
    }

    /**
     * Fake port that returns a configurable result or throws a configurable exception,
     * and records the last normalized number it was called with.
     */
    private static final class FakeProcessLookupPort implements ProcessLookupPort {

        private Optional<ProcessLookupResult> result = Optional.empty();
        private RuntimeException exceptionToThrow;
        private String lastQuery;
        private int lookupCalls;

        @Override
        public Optional<ProcessLookupResult> lookup(String cnjNumber) {
            lookupCalls++;
            lastQuery = cnjNumber;
            if (exceptionToThrow != null) {
                throw exceptionToThrow;
            }
            return result;
        }
    }
}
