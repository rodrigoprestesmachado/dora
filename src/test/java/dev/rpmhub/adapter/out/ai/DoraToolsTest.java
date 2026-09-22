package dev.rpmhub.adapter.out.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.rpmhub.domain.port.in.ProcessLookupUseCase;

/**
 * Plain unit tests for {@link DoraTools}, without booting Quarkus/LangChain4j.
 *
 * @author Rodrigo Prestes Machado
 */
@ExtendWith(MockitoExtension.class)
class DoraToolsTest {

    @Mock
    private ProcessLookupUseCase processLookupUseCase;

    private DoraTools doraTools;

    @BeforeEach
    void setUp() {
        doraTools = new DoraTools(processLookupUseCase);
    }

    @Test
    void lookupTjrsProcess_delegatesToProcessLookupUseCase() {
        when(processLookupUseCase.lookup("5033013-66.2026.8.21.0022")).thenReturn("# Andamento do processo");

        String result = doraTools.lookupTjrsProcess("5033013-66.2026.8.21.0022");

        assertEquals("# Andamento do processo", result);
        verify(processLookupUseCase).lookup("5033013-66.2026.8.21.0022");
    }
}
