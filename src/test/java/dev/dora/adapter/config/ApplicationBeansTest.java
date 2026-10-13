package dev.dora.adapter.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import dev.dora.adapter.out.ai.DoraAgent;
import dev.dora.application.ChatService;
import dev.dora.application.ProcessLookupService;
import dev.dora.domain.model.ProcessLookupResult;
import dev.dora.domain.model.RagQuery;
import dev.dora.domain.model.RagResponse;
import dev.dora.domain.port.in.ChatUseCase;
import dev.dora.domain.port.in.ProcessLookupUseCase;
import dev.dora.domain.port.out.EmbeddingRepository;
import dev.dora.domain.port.out.ProcessLookupPort;
import dev.dora.domain.port.out.Repository;
import io.smallrye.mutiny.Multi;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @Mock
    private SchedulingConfig schedulingConfig;

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
        applicationBeans.schedulingConfig = schedulingConfig;
        applicationBeans.processLookupPort = processLookupPort;
        applicationBeans.clientNotice = text -> {
        };
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
        when(schedulingConfig.timezone()).thenReturn("America/Sao_Paulo");
        when(doraAgent.answer(eq("5511999999999"), eq(""), contains("Data de hoje")))
                .thenReturn(Multi.createFrom().items("resposta"));

        ChatUseCase chatUseCase = applicationBeans.chatUseCase();

        assertInstanceOf(ChatService.class, chatUseCase);
        List<String> chunks = chatUseCase.chat("5511999999999",
                "oi").collect().asList().await().indefinitely();
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
