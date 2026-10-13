package dev.dora.adapter.in.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.dora.domain.port.in.ChatUseCase;
import io.smallrye.mutiny.Multi;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Plain unit tests for {@link DoraResource}, without booting Quarkus.
 *
 * @author Rodrigo Prestes Machado
 */
@ExtendWith(MockitoExtension.class)
class AgentResourceUnitTest {

    /**
     * Driving port mocked to isolate the REST adapter under test.
     */
    @Mock
    private ChatUseCase chatUseCase;

    /**
     * Resource under test.
     */
    private DoraResource agentResource;

    /**
     * Wires the resource with the mocked use case before each test.
     */
    @BeforeEach
    void setUp() {
        agentResource = new DoraResource(chatUseCase);
    }

    /**
     * Ensures the resource forwards the form parameters to the use case and
     * streams back its response unchanged.
     */
    @Test
    void chat_delegatesToChatUseCaseWithSubmittedParameters() {
        when(chatUseCase.chat("5511999999999", "oi")).thenReturn(Multi.createFrom().items("re",
                "sposta"));

        List<String> chunks = agentResource.chat("5511999999999", "oi")
                .collect().asList().await().indefinitely();

        assertEquals(List.of("re", "sposta"), chunks);
        verify(chatUseCase).chat("5511999999999", "oi");
    }

}
