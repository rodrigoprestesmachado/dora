package dev.dora.adapter.out.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.dora.adapter.ConversationPhone;
import dev.dora.domain.port.in.CancelMeetingUseCase;
import dev.dora.domain.port.in.CheckAvailabilityUseCase;
import dev.dora.domain.port.in.FindMeetingsUseCase;
import dev.dora.domain.port.in.ProcessLookupUseCase;
import dev.dora.domain.port.in.RescheduleMeetingUseCase;
import dev.dora.domain.port.in.ScheduleMeetingUseCase;
import dev.dora.domain.port.in.SuggestMeetingSlotsUseCase;
import dev.dora.domain.port.out.ClientNoticePort;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolProvider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Checks that the tool texts stay out of Java and still reach the model with the same wording.
 *
 * @author Rodrigo Prestes Machado
 */
@ExtendWith(MockitoExtension.class)
class DoraToolCatalogTest {

    private static final String CANCEL_TEXT = "Cancela uma reunião criada pela Dora. "
            + "Ação destrutiva: só passe confirmedByUser=true depois que o cliente confirmar "
            + "explicitamente, e a mensagem de confirmação deve citar o título e a data/hora. "
            + "Não cancela reunião que a Dora não marcou.";

    @Mock
    private ProcessLookupUseCase processLookupUseCase;

    @Mock
    private CheckAvailabilityUseCase checkAvailabilityUseCase;

    @Mock
    private SuggestMeetingSlotsUseCase suggestMeetingSlotsUseCase;

    @Mock
    private ScheduleMeetingUseCase scheduleMeetingUseCase;

    @Mock
    private FindMeetingsUseCase findMeetingsUseCase;

    @Mock
    private RescheduleMeetingUseCase rescheduleMeetingUseCase;

    @Mock
    private CancelMeetingUseCase cancelMeetingUseCase;

    @Mock
    private ClientNoticePort clientNotice;

    private DoraTools doraTools;

    private Map<String, AiServiceTool> tools;

    @BeforeEach
    void setUp() {
        doraTools = new DoraTools(processLookupUseCase, checkAvailabilityUseCase,
                suggestMeetingSlotsUseCase, scheduleMeetingUseCase, findMeetingsUseCase,
                rescheduleMeetingUseCase, cancelMeetingUseCase, clientNotice);
        ToolProvider provider = new DoraToolCatalog(doraTools).get();
        tools = new LinkedHashMap<>();
        for (AiServiceTool tool : provider.provideTools(null).aiServiceTools()) {
            tools.put(tool.name(), tool);
        }
    }

    @Test
    void descriptionsComeFromThePromptFile() {
        DoraToolTexts texts = DoraToolTexts.load();
        assertEquals(24, texts.keys().size());
        assertEquals(CANCEL_TEXT, texts.text("cancel_meeting"));
        for (String value : texts.keys()) {
            assertFalse(texts.text(value).contains("  "));
        }

        ToolSpecification cancel = tools.get("cancel_meeting").toolSpecification();
        assertEquals(CANCEL_TEXT, cancel.description());
        assertEquals(texts.text("cancel_meeting.confirmedByUser"),
                cancel.parameters().properties().get("confirmedByUser").description());
        assertTrue(cancel.parameters().required().contains("confirmedByUser"));

        ToolSpecification lookup = tools.get("lookupTjrsProcess").toolSpecification();
        assertNull(lookup.parameters().properties().get("processNumber").description());
        assertEquals(7, tools.size());
        assertFalse(tools.containsKey("check_availability"));
    }

    @Test
    void eachToolDelegatesToTheUseCase() {
        when(processLookupUseCase.lookup("5033013-66.2026.8.21.0022")).thenReturn("andamento");
        when(processLookupUseCase.searchByPerson("Maria Souza")).thenReturn("pessoas");
        when(suggestMeetingSlotsUseCase.suggest("2026-10-13", null, "evt-1")).thenReturn("opções");
        when(scheduleMeetingUseCase.schedule("2026-10-13T09:00", "Consulta",
                "ana@escritorio.com", "online", "contrato")).thenReturn("marcada");
        when(findMeetingsUseCase.find(null, null, null, null)).thenReturn("achei");
        when(rescheduleMeetingUseCase.reschedule("evt-1", "2026-10-14T09:00")).thenReturn("movida");
        when(cancelMeetingUseCase.cancel("evt-1", "motivo", true)).thenReturn("cancelada");

        assertEquals("andamento", execute("lookupTjrsProcess",
                "{\"processNumber\":\"5033013-66.2026.8.21.0022\"}", "5551999999999"));
        assertNull(ConversationPhone.current());
        assertEquals("pessoas", execute("searchTjrsProcessesByPerson",
                "{\"personName\":\"Maria Souza\"}", null));
        assertEquals("opções", execute("suggest_meeting_slots",
                "{\"fromDate\":\"2026-10-13\",\"excludeEventId\":\"evt-1\"}", null));
        assertEquals("marcada", execute("schedule_meeting",
                "{\"startDateTime\":\"2026-10-13T09:00\",\"title\":\"Consulta\","
                        + "\"attendeeEmails\":[\"ana@escritorio.com\"],\"mode\":\"online\","
                        + "\"notes\":\"contrato\"}",
                null));
        assertEquals("achei", execute("find_meetings", "", null));
        assertEquals("movida", execute("reschedule_meeting",
                "{\"eventId\":\"evt-1\",\"newStartDateTime\":\"2026-10-14T09:00\"}", null));
        assertEquals("cancelada", execute("cancel_meeting",
                "{\"eventId\":\"evt-1\",\"reason\":\"motivo\",\"confirmedByUser\":true}", null));

        verify(processLookupUseCase).lookup("5033013-66.2026.8.21.0022");
        verify(scheduleMeetingUseCase).schedule("2026-10-13T09:00", "Consulta",
                "ana@escritorio.com", "online", "contrato");
        verify(cancelMeetingUseCase).cancel("evt-1", "motivo", true);
    }

    @Test
    void invalidArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> execute("cancel_meeting", "{", null));
        assertThrows(IllegalArgumentException.class,
                () -> execute("schedule_meeting", "{\"attendeeEmails\":\"ana@escritorio.com\"}",
                        null));
    }

    private String execute(String name, String arguments, Object memoryId) {
        return tools.get(name).toolExecutor().execute(ToolExecutionRequest.builder()
                .name(name)
                .arguments(arguments)
                .build(), memoryId);
    }
}
