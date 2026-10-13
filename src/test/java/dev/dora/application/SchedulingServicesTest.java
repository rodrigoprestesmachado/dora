package dev.dora.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dora.domain.model.CalendarEvent;
import dev.dora.domain.model.MeetingMode;
import dev.dora.domain.model.SchedulingRules;
import dev.dora.domain.port.out.CalendarAccessException;
import dev.dora.domain.port.out.CalendarConflictException;
import dev.dora.domain.port.out.CalendarEventNotFoundException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the scheduling use cases. The calendar port is in-memory; Quarkus is not started.
 *
 * @author Rodrigo Prestes Machado
 */
class SchedulingServicesTest {

    private RecordingCalendarPort calendar;
    private Clock clock;
    private SchedulingRules rules;

    @BeforeEach
    void setUp() {
        calendar = new RecordingCalendarPort();
        clock = Clock.fixed(SchedulingFixtures.NOW, SchedulingFixtures.ZONE);
        rules = SchedulingFixtures.rules();
    }

    @Test
    void checkAvailability_hidesBusySlotsAndReportsAnEmptyWindow() {
        calendar.events.add(meeting("busy", "Ocupado", "2026-10-13T09:00", "2026-10-13T10:00",
                false));

        String result = new CheckAvailabilityService(calendar, rules, clock).check(null, null);

        assertTrue(result.contains("2026-10-13T10:00"));
        assertFalse(result.contains("2026-10-13T09:00"));
        assertFalse(result.contains("2026-10-16"));

        calendar.events.clear();
        String none = new CheckAvailabilityService(calendar, rules, clock).check("2026-10-16",
                "2026-10-16");
        assertEquals(SchedulingTexts.NO_SLOTS, none);
    }

    @Test
    void suggest_waitsForAChoiceOrBooksTheEarliestSlot() {
        String choice = new SuggestMeetingSlotsService(calendar, rules, clock).suggest(null, null,
                null);
        assertTrue(choice.contains("aguarde a escolha"));
        assertTrue(choice.contains("janela de 30 dias"));
        assertTrue(choice.contains("Não diga que a agenda ainda não está aberta"));
        assertTrue(choice.contains("2026-10-13T09:00"));
        assertTrue(choice.contains("2026-10-13T14:00"));
        assertFalse(choice.contains("2026-10-14T09:00"));

        SchedulingRules automatic = SchedulingFixtures.rules(60, false, 3, 24, Optional.of(2), true,
                30);
        String direct = new SuggestMeetingSlotsService(calendar, automatic, clock).suggest(null,
                null, null);
        assertTrue(direct.contains("marque diretamente"));
        assertTrue(direct.contains("2026-10-13T09:00"));
        assertFalse(direct.contains("2026-10-14T09:00"));
    }

    @Test
    void suggest_rejectsAPastMonthAndKeepsTheLastWeekOfThisMonth() {
        String past = new SuggestMeetingSlotsService(calendar, rules, clock).suggest("2024-06-24",
                "2024-06-30", null);
        assertTrue(past.contains("2024-06-24"));
        assertTrue(past.contains("estão fora da janela de busca"));
        assertTrue(past.contains("12/10/2026"));
        assertTrue(past.contains("Não diga ao cliente que não há horários"));
        assertFalse(past.contains("Não há horários livres"));

        String lastWeek = new SuggestMeetingSlotsService(calendar, rules, clock).suggest(
                "2026-10-26", "2026-10-31", null);
        assertTrue(lastWeek.contains("2026-10-26"));
        assertTrue(lastWeek.contains("aguarde a escolha"));
    }

    @Test
    void suggest_ignoresTheMeetingBeingMoved() {
        calendar.events.add(meeting("mine", "Consulta", "2026-10-13T09:00", "2026-10-13T10:00",
                true));

        String result = new SuggestMeetingSlotsService(calendar, rules, clock).suggest("2026-10-13",
                "2026-10-13", 
                "mine");

        assertTrue(result.contains("2026-10-13T09:00"));
    }

    @Test
    void schedule_createsTheMeetingAndCanBeRepeatedWithoutASecondEvent() {
        ScheduleMeetingService service = new ScheduleMeetingService(calendar, rules, clock,
                SchedulingFixtures.OFFICE);

        String created = service.schedule("2026-10-13T09:00", "Consulta",
                "Ana@Escritorio.com, ana@escritorio.com", "online", null);

        assertEquals(1, calendar.createCalls);
        assertTrue(calendar.lastCreateNotified);
        assertTrue(created.contains("Reunião marcada"));
        assertTrue(created.contains("13/10/2026"));
        assertTrue(created.contains("09:00"));
        assertTrue(created.contains("America/Sao_Paulo"));
        assertTrue(created.contains("https://meet.google.com/abc-defg-hij"));
        assertTrue(created.contains("created-1"));

        String again = service.schedule("2026-10-13T09:00", "consulta", "ana@escritorio.com",
                "online", null);
        assertEquals(1, calendar.createCalls);
        assertTrue(again.contains("já estava marcada"));
        assertTrue(again.contains("https://meet.google.com/abc-defg-hij"));
    }

    @Test
    void schedule_refusesATakenSlotAndDoesNotCreate() {
        calendar.events.add(meeting("other", "Outra", "2026-10-13T09:00", "2026-10-13T10:00",
                false));

        String result = new ScheduleMeetingService(calendar, rules, clock,
                SchedulingFixtures.OFFICE).schedule("2026-10-13T09:00", "Consulta",
                "ana@escritorio.com", "online", null);

        assertEquals(0, calendar.createCalls);
        assertTrue(result.contains("não está mais livre"));
        assertTrue(result.contains("2026-10-13T10:00"));
    }

    @Test
    void schedule_turnsAProviderConflictIntoNewOptions() {
        calendar.onWrite = new CalendarConflictException("duplicate");

        String result = new ScheduleMeetingService(calendar, rules, clock,
                SchedulingFixtures.OFFICE).schedule("2026-10-13T09:00", "Consulta",
                "ana@escritorio.com", "online", null);

        assertEquals(1, calendar.createCalls);
        assertTrue(result.contains("não está mais livre"));
        assertTrue(calendar.events.isEmpty());
    }

    @Test
    void schedule_hidesAProviderOutage() {
        calendar.onRead = new CalendarAccessException("connection reset");

        String result = new ScheduleMeetingService(calendar, rules, clock,
                SchedulingFixtures.OFFICE).schedule("2026-10-13T09:00", "Consulta",
                "ana@escritorio.com", "online", null);

        assertEquals(SchedulingTexts.UNAVAILABLE, result);
        assertEquals(0, calendar.createCalls);
    }

    @Test
    void schedule_respectsNotifyAttendees() {
        SchedulingRules quiet = SchedulingFixtures.rules(60, true, 3, 24, Optional.of(2), false,
                30);

        new ScheduleMeetingService(calendar, quiet, clock,
                SchedulingFixtures.OFFICE).schedule("2026-10-13T09:00", "Consulta",
                "ana@escritorio.com", "online", null);

        assertFalse(calendar.lastCreateNotified);
    }

    @Test
    void schedule_onlineRequiresAnEmailAndRejectsAnUnknownMode() {
        ScheduleMeetingService service = new ScheduleMeetingService(calendar, rules, clock,
                SchedulingFixtures.OFFICE);

        String missingOnline = service.schedule("2026-10-13T09:00", "Ana Souza", null, "online",
                null);
        String missingInPerson = service.schedule("2026-10-13T09:00", "Ana Souza", " ",
                "presencial", null);
        String unknownMode = service.schedule("2026-10-13T09:00", "Ana Souza", "ana@escritorio.com",
                "hibrida", null);

        assertTrue(missingOnline.contains("e-mail"));
        assertTrue(missingInPerson.contains("e-mail"));
        assertTrue(unknownMode.contains("online ou presencial"));
        assertEquals(0, calendar.createCalls);
    }

    @Test
    void schedule_inPersonStoresTheOfficeAndSkipsTheMeetLink() {
        ScheduleMeetingService service = new ScheduleMeetingService(calendar, rules, clock,
                SchedulingFixtures.OFFICE);

        String created = service.schedule("2026-10-13T09:00", "Ana Souza", "ana@escritorio.com",
                "presencial", "Quero falar sobre um contrato.");

        assertEquals(1, calendar.createCalls);
        assertEquals(MeetingMode.IN_PERSON, calendar.lastCreated.getMode());
        assertEquals(SchedulingFixtures.OFFICE, calendar.lastCreated.getLocation());
        assertEquals(List.of("ana@escritorio.com"), calendar.lastCreated.getAttendeeEmails());
        assertEquals("Quero falar sobre um contrato.", calendar.lastCreated.getNotes());
        assertTrue(calendar.lastCreateNotified);
        assertTrue(created.contains("Reunião marcada"));
        assertTrue(created.contains(SchedulingFixtures.OFFICE));
        assertTrue(created.contains("convite da agenda foi enviado para ana@escritorio.com"));
        assertTrue(created.contains("Notas: Quero falar sobre um contrato."));
        assertTrue(created.contains("assunto foi anotado"));
        assertFalse(created.contains("meet.google.com"));

        String again = service.schedule("2026-10-13T09:00", "ana souza", "ana@escritorio.com",
                "presencial", null);
        assertEquals(1, calendar.createCalls);
        assertTrue(again.contains("já estava marcada"));
        assertTrue(again.contains(SchedulingFixtures.OFFICE));
    }

    @Test
    void find_returnsOnlyFutureMeetingsCreatedByDora() {
        calendar.events.add(meeting("past", "Passada", "2026-10-11T09:00", "2026-10-11T10:00",
                true));
        calendar.events.add(meeting("other", "Alheia", "2026-10-13T14:00", "2026-10-13T15:00",
                false));
        calendar.events.add(meeting("mine", "Consulta inicial", "2026-10-13T10:00",
                "2026-10-13T11:00", true, 
                List.of("ana@escritorio.com"), "https://meet.google.com/abc-defg-hij"));
        calendar.events.add(meeting("later", "Outra consulta", "2026-10-20T10:00",
                "2026-10-20T11:00", true, 
                List.of("bruno@escritorio.com"), "https://meet.google.com/xyz"));

        FindMeetingsService service = new FindMeetingsService(calendar, rules, clock);
        String one = service.find("ana@escritorio.com", null, null, "inicial");
        assertTrue(one.contains("Encontrei uma reunião"));
        assertTrue(one.contains("mine"));
        assertFalse(one.contains("later"));
        assertFalse(one.contains("Alheia"));

        String many = service.find(null, null, null, "consulta");
        assertTrue(many.contains("mais de uma"));
        assertTrue(many.contains("qual deseja alterar"));
    }

    @Test
    void reschedule_movesTheSameEventAndIgnoresItsCurrentTime() {
        SchedulingRules halfHour = SchedulingFixtures.rules(30, true, 3, 24, Optional.of(2), true,
                30);
        calendar.events.add(meeting("mine", "Consulta", "2026-10-13T09:00", "2026-10-13T10:00",
                true, 
                List.of("ana@escritorio.com"), "https://meet.google.com/abc-defg-hij"));

        String result = new RescheduleMeetingService(calendar, halfHour, clock,
                SchedulingFixtures.OFFICE).reschedule("mine", "2026-10-13T09:30");

        assertEquals(0, calendar.createCalls);
        assertEquals(1, calendar.updateCalls);
        assertEquals("mine", calendar.lastUpdatedId);
        assertEquals(SchedulingFixtures.at("2026-10-13T09:30"), calendar.lastUpdatedStart);
        assertTrue(calendar.lastUpdateNotified);
        assertTrue(result.contains("reagendada"));
        assertTrue(result.contains("https://meet.google.com/abc-defg-hij"));
        assertTrue(result.contains("ID: mine"));
        assertTrue(result.contains("continua o mesmo"));
    }

    @Test
    void reschedule_reportsNoChangeForTheSameStart() {
        calendar.events.add(meeting("mine", "Consulta", "2026-10-13T09:00", "2026-10-13T10:00",
                true));

        String result = new RescheduleMeetingService(calendar, rules, clock,
                SchedulingFixtures.OFFICE).reschedule("mine", "2026-10-13T09:00");

        assertEquals(0, calendar.updateCalls);
        assertTrue(result.contains("Não há mudança"));
    }

    @Test
    void reschedule_refusesForeignPastAndTooSoonMeetings() {
        RescheduleMeetingService service = new RescheduleMeetingService(calendar, rules, clock,
                SchedulingFixtures.OFFICE);
        calendar.events.add(meeting("foreign", "Alheia", "2026-10-13T14:00", "2026-10-13T15:00",
                false));
        assertTrue(service.reschedule("foreign",
                "2026-10-14T09:00").contains("não foi criada por mim"));

        calendar.events.clear();
        calendar.events.add(meeting("past", "Passada", "2026-10-11T09:00", "2026-10-11T10:00",
                true));
        assertTrue(service.reschedule("past", "2026-10-14T09:00").contains("já aconteceu"));

        calendar.events.clear();
        calendar.events.add(meeting("soon", "Quase", "2026-10-12T09:30", "2026-10-12T10:30", true));
        String tooSoon = service.reschedule("soon", "2026-10-14T09:00");
        assertTrue(tooSoon.contains("pouca antecedência"));
        assertTrue(tooSoon.contains("participantes"));
        assertEquals(0, calendar.updateCalls);
    }

    @Test
    void reschedule_missingMeetingAndFailedUpdateLeaveTheOriginalUntouched() {
        RescheduleMeetingService service = new RescheduleMeetingService(calendar, rules, clock,
                SchedulingFixtures.OFFICE);
        String missing = service.reschedule("nope", "2026-10-14T09:00");
        assertTrue(missing.contains("já ter sido cancelada"));
        assertEquals(0, calendar.updateCalls);

        CalendarEvent original = meeting("mine", "Consulta", "2026-10-14T09:00", "2026-10-14T10:00",
                true);
        calendar.events.add(original);
        calendar.onWrite = new CalendarAccessException("timeout");
        String failed = service.reschedule("mine", "2026-10-15T09:00");
        assertEquals(SchedulingTexts.UNAVAILABLE, failed);
        assertEquals(original.getStart(), calendar.events.get(0).getStart());
    }

    @Test
    void reschedule_providerConflictDoesNotCreateAnotherEvent() {
        calendar.events.add(meeting("mine", "Consulta", "2026-10-14T09:00", "2026-10-14T10:00",
                true));
        calendar.onWrite = new CalendarConflictException("conflict");

        String result = new RescheduleMeetingService(calendar, rules, clock,
                SchedulingFixtures.OFFICE).reschedule("mine", "2026-10-15T09:00");

        assertEquals(0, calendar.createCalls);
        assertEquals(1, calendar.updateCalls);
        assertTrue(result.contains("continua no horário anterior"));
    }

    @Test
    void reschedule_inPersonConfirmsTheOfficeAddress() {
        calendar.events.add(meeting("mine", "Ana Souza", "2026-10-14T09:00", "2026-10-14T10:00",
                true, List.of(), null));

        String result = new RescheduleMeetingService(calendar, rules, clock,
                SchedulingFixtures.OFFICE)
                .reschedule("mine", "2026-10-15T09:00");

        assertTrue(result.contains("reagendada"));
        assertTrue(result.contains(SchedulingFixtures.OFFICE));
        assertTrue(result.contains("endereço"));
        assertFalse(result.contains("meet.google.com"));
        assertFalse(result.contains("link"));
    }

    @Test
    void cancel_requiresConfirmationAndOnlyCancelsDoraMeetings() {
        calendar.events.add(meeting("mine", "Consulta", "2026-10-14T09:00", "2026-10-14T10:00",
                true, 
                List.of("ana@escritorio.com"), "https://meet.google.com/abc-defg-hij"));
        CancelMeetingService service = new CancelMeetingService(calendar, rules, clock);

        String ask = service.cancel("mine", null, false);
        assertEquals(0, calendar.cancelCalls);
        assertTrue(ask.contains("confirme com o cliente"));
        assertTrue(ask.contains("Consulta"));
        assertTrue(ask.contains("14/10/2026"));

        String done = service.cancel("mine", "cliente desmarcou", true);
        assertEquals(1, calendar.cancelCalls);
        assertTrue(calendar.lastCancelNotified);
        assertTrue(done.contains("Reunião cancelada"));
        assertTrue(done.contains("cliente desmarcou"));
        assertTrue(done.contains("ID: mine"));
    }

    @Test
    void cancel_refusesTooSoonForeignAndUnknownMeetings() {
        CancelMeetingService service = new CancelMeetingService(calendar, rules, clock);
        calendar.events.add(meeting("soon", "Quase", "2026-10-12T09:30", "2026-10-12T10:30", true));
        assertTrue(service.cancel("soon", null, true).contains("pouca antecedência"));

        calendar.events.clear();
        calendar.events.add(meeting("foreign", "Alheia", "2026-10-14T09:00", "2026-10-14T10:00",
                false));
        assertTrue(service.cancel("foreign", null, true).contains("não foi criada por mim"));

        calendar.events.clear();
        assertTrue(service.cancel("gone", null, true).contains("já ter sido cancelada"));
        assertEquals(0, calendar.cancelCalls);
    }

    @Test
    void cancel_isSafeToRepeatWhenTheProviderAlreadyRemovedTheEvent() {
        calendar.events.add(meeting("mine", "Consulta", "2026-10-14T09:00", "2026-10-14T10:00",
                true));
        calendar.onWrite = new CalendarEventNotFoundException("gone");

        String result = new CancelMeetingService(calendar, rules, clock).cancel("mine", null, true);

        assertTrue(result.contains("já ter sido cancelada"));
        assertEquals(calendar.events.get(0).getStart(), SchedulingFixtures.at("2026-10-14T09:00"));
    }

    @Test
    void cancel_withoutChangeNoticeAllowsAMeetingInsideTwoHours() {
        SchedulingRules open = SchedulingFixtures.rules(60, true, 3, 0, Optional.empty(), true, 30);
        calendar.events.add(meeting("soon", "Quase", "2026-10-12T09:30", "2026-10-12T10:30", true));

        String result = new CancelMeetingService(calendar, open, clock).cancel("soon", null, true);

        assertEquals(1, calendar.cancelCalls);
        assertTrue(result.contains("Reunião cancelada"));
    }

    private static CalendarEvent meeting(String id, String title, String start, String end,
            boolean createdByDora) {
        return meeting(id, title, start, end, createdByDora, List.of("ana@escritorio.com"),
                "https://meet.google.com/abc-defg-hij");
    }

    private static CalendarEvent meeting(String id, String title, String start, String end,
            boolean createdByDora, 
            List<String> attendees, String link) {
        return new CalendarEvent(id, title, SchedulingFixtures.at(start),
                SchedulingFixtures.at(end), false, attendees, 
                link, createdByDora, false, false, false);
    }
}
