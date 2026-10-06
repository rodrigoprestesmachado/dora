package dev.rpmhub.adapter.out.tjrs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for trimming the TJRS process-summary movements table.
 *
 * @author Rodrigo Prestes Machado
 */
class TjrsProcessPageParserTest {

    private final TjrsProcessPageParser parser = new TjrsProcessPageParser();

    @Test
    void keepLatestMovement_keepsTheNewestRowAndTheRestOfThePage() {
        String html = """
                <div class="content">
                  <p>Comarca: Porto Alegre</p>
                  <app-movimentos-resumo>
                    <h3>Últimas Movimentações</h3>
                    <table aria-label="Elements">
                      <tr><th>Data</th><th>Descrição</th></tr>
                      <tr><td>01/01/2026</td><td>Juntada antiga</td></tr>
                      <tr><td>05/10/2026 14:30</td><td>Conclusos para decisão</td></tr>
                    </table>
                  </app-movimentos-resumo>
                </div>
                """;

        String result = parser.keepLatestMovement(html);

        assertTrue(result.contains("Comarca: Porto Alegre"));
        assertTrue(result.contains("Últimas Movimentações"));
        assertTrue(result.contains("<th>Data</th>"));
        assertTrue(result.contains("05/10/2026 14:30"));
        assertTrue(result.contains("Conclusos para decisão"));
        assertFalse(result.contains("01/01/2026"));
        assertFalse(result.contains("Juntada antiga"));
    }

    @Test
    void keepLatestMovement_breaksDateTiesByTheHighestEventNumber() {
        String html = """
                <app-movimentos-resumo>
                  <table>
                    <tr><th>Evento</th><th>Data</th><th>Descrição</th></tr>
                    <tr><td>10</td><td>01/02/2026</td><td>Evento menor</td></tr>
                    <tr><td>12</td><td>01/02/2026 00:00</td><td>Evento maior</td></tr>
                  </table>
                </app-movimentos-resumo>
                """;

        String result = parser.keepLatestMovement(html);

        assertTrue(result.contains("Evento maior"));
        assertFalse(result.contains("Evento menor"));
    }

    @Test
    void keepLatestMovement_keepsTheFirstRowWhenNoDateIsPresent() {
        String html = """
                <app-movimentos-resumo>
                  <table>
                    <tr><td>Primeira linha</td></tr>
                    <tr><td>Segunda linha</td></tr>
                  </table>
                </app-movimentos-resumo>
                """;

        String result = parser.keepLatestMovement(html);

        assertTrue(result.contains("Primeira linha"));
        assertFalse(result.contains("Segunda linha"));
    }

    @Test
    void keepLatestMovement_leavesHtmlWithoutTheMovementsSectionUntouched() {
        String html = "<div>Número do Processo: 5033013-66.2026.8.21.0022</div>";

        assertEquals(html, parser.keepLatestMovement(html));
        assertEquals(null, parser.keepLatestMovement(null));
    }
}
