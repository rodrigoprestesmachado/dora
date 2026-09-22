package dev.rpmhub.adapter.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.rpmhub.adapter.out.ai.DoraAgent;
import dev.rpmhub.application.ChatService;
import dev.rpmhub.application.ProcessLookupService;
import dev.rpmhub.domain.model.ProcessLookupResult;
import dev.rpmhub.domain.model.RagQuery;
import dev.rpmhub.domain.model.RagResponse;
import dev.rpmhub.domain.port.in.ChatUseCase;
import dev.rpmhub.domain.port.in.ProcessLookupUseCase;
import dev.rpmhub.domain.port.out.EmbeddingRepository;
import dev.rpmhub.domain.port.out.ProcessLookupPort;
import dev.rpmhub.domain.port.out.Repository;
import io.smallrye.mutiny.Multi;

/**
 * Unit tests for {@link ApplicationBeans}.
 *
 * <p>Verifies that the CDI wiring class produces a working {@link ChatUseCase}
 * backed by a plain {@link ChatService}, without booting a CDI container.
 *
 * @author Rodrigo Prestes Machado
 */
@ExtendWith(MockitoExtension.class)
class ApplicationBeansTest {

    /**
     * Driven port mocked to isolate the wiring under test.
     */
    @Mock
    private Repository chatRepository;

    /**
     * Driven port mocked to isolate the wiring under test.
     */
    @Mock
    private EmbeddingRepository embeddingRepository;

    /**
     * Driven port mocked to isolate the wiring under test.
     */
    @Mock
    private DoraAgent doraAgent;

    /**
     * Driven port mocked to isolate the wiring under test.
     */
    @Mock
    private ProcessLookupPort processLookupPort;

    /**
     * Wiring class under test.
     */
    private ApplicationBeans applicationBeans;

    /**
     * Wires the beans class with mocked ports before each test.
     */
    @BeforeEach
    void setUp() {
        applicationBeans = new ApplicationBeans();
        applicationBeans.chatRepository = chatRepository;
        applicationBeans.embeddingRepository = embeddingRepository;
        applicationBeans.doraAgent = doraAgent;
        applicationBeans.processLookupPort = processLookupPort;
    }

    /**
     * Ensures the produced use case is a plain, framework-free {@link ChatService}
     * wired with the injected ports.
     */
    @Test
    void chatUseCase_producesChatServiceWiredWithInjectedPorts() {
        when(chatRepository.findLastByPhone("5511999999999")).thenReturn(Optional.empty());
        when(embeddingRepository.searchChunks(org.mockito.ArgumentMatchers.any(RagQuery.class)))
                .thenReturn(new RagResponse("oi", List.of(), 0.0));
        when(doraAgent.answer("5511999999999", "", "oi")).thenReturn(Multi.createFrom().items("resposta"));

        ChatUseCase chatUseCase = applicationBeans.chatUseCase();

        assertInstanceOf(ChatService.class, chatUseCase);
        List<String> chunks = chatUseCase.chat("5511999999999", "oi").collect().asList().await().indefinitely();
        assertEquals(List.of("resposta"), chunks);
    }

    /**
     * Ensures the produced use case is a plain, framework-free {@link ProcessLookupService}
     * wired with the injected lookup port.
     */
    @Test
    void processLookupUseCase_producesProcessLookupServiceWiredWithInjectedPort() {
        String cnjNumber = "5033013-66.2026.8.21.0022";
        when(processLookupPort.lookup(cnjNumber))
                .thenReturn(Optional.of(new ProcessLookupResult(cnjNumber, "# Andamento", "https://x")));

        ProcessLookupUseCase processLookupUseCase = applicationBeans.processLookupUseCase();

        assertInstanceOf(ProcessLookupService.class, processLookupUseCase);
        assertEquals("# Andamento", processLookupUseCase.lookup(cnjNumber));
    }

}
