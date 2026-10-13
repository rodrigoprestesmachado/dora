package dev.dora.adapter.out.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Plain unit tests for {@link DoraTools}, without booting Quarkus/LangChain4j.
 *
 * @author Rodrigo Prestes Machado
 */
@ExtendWith(MockitoExtension.class)
class DoraToolsTest {

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

    @BeforeEach
    void setUp() {
        doraTools = new DoraTools(processLookupUseCase, checkAvailabilityUseCase,
                suggestMeetingSlotsUseCase, 
                scheduleMeetingUseCase, findMeetingsUseCase, rescheduleMeetingUseCase,
                        cancelMeetingUseCase, 
                clientNotice);
    }

    @Test
    void lookupTjrsProcess_delegatesToProcessLookupUseCase() {
        when(processLookupUseCase.lookup("5033013-66.2026.8.21.0022"))
                .thenReturn("# Andamento do processo");

        String result = doraTools.lookupTjrsProcess("5033013-66.2026.8.21.0022", null);

        assertEquals("# Andamento do processo", result);
        verify(processLookupUseCase).lookup("5033013-66.2026.8.21.0022");
    }

    @Test
    void searchTjrsProcessesByPerson_delegatesToProcessLookupUseCase() {
        when(processLookupUseCase.searchByPerson("Maria Souza")).thenAnswer(invocation -> {
            assertEquals("5551999999999", ConversationPhone.current());
            return "| processos |";
        });

        String result = doraTools.searchTjrsProcessesByPerson("Maria Souza", "5551999999999");

        assertEquals("| processos |", result);
        verify(processLookupUseCase).searchByPerson("Maria Souza");
        assertNull(ConversationPhone.current());
    }

    @Test
    void schedulingTools_delegateToTheUseCases() {
        when(checkAvailabilityUseCase.check("2026-10-13", "2026-10-20")).thenReturn("livres");
        when(suggestMeetingSlotsUseCase.suggest("2026-10-13", null, "evt-1")).thenReturn("opções");
        when(scheduleMeetingUseCase.schedule("2026-10-13T09:00", "Consulta", "ana@escritorio.com",
                "online", "contrato"))
                .thenReturn("marcada");
        when(findMeetingsUseCase.find("ana@escritorio.com", null, null,
                "Consulta")).thenReturn("achei");
        when(rescheduleMeetingUseCase.reschedule("evt-1", "2026-10-14T09:00")).thenReturn("movida");
        when(cancelMeetingUseCase.cancel("evt-1", "motivo", true)).thenReturn("cancelada");

        assertEquals("livres", doraTools.checkAvailability("2026-10-13", "2026-10-20"));
        assertEquals("opções", doraTools.suggestMeetingSlots("2026-10-13", null, "evt-1"));
        assertEquals("marcada",
                doraTools.scheduleMeeting("2026-10-13T09:00", "Consulta",
                        List.of("ana@escritorio.com"), "online", "contrato"));
        assertEquals("achei", doraTools.findMeetings("ana@escritorio.com", null, null, "Consulta"));
        assertEquals("movida", doraTools.rescheduleMeeting("evt-1", "2026-10-14T09:00"));
        assertEquals("cancelada", doraTools.cancelMeeting("evt-1", "motivo", true));

        verify(checkAvailabilityUseCase).check("2026-10-13", "2026-10-20");
        verify(suggestMeetingSlotsUseCase).suggest("2026-10-13", null, "evt-1");
        verify(scheduleMeetingUseCase).schedule("2026-10-13T09:00", "Consulta",
                "ana@escritorio.com", "online", "contrato");
        verify(findMeetingsUseCase).find("ana@escritorio.com", null, null, "Consulta");
        verify(rescheduleMeetingUseCase).reschedule("evt-1", "2026-10-14T09:00");
        verify(cancelMeetingUseCase).cancel("evt-1", "motivo", true);
    }
}
