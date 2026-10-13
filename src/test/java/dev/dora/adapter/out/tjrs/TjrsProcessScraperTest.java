package dev.dora.adapter.out.tjrs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dora.adapter.out.rag.HtmlToMarkdown;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the "not found" detection logic in {@link TjrsProcessScraper}.
 *
 * <p>The default markers below were derived from a real, live query against
 * {@code consulta.tjrs.jus.br/consulta-processual} for a non-existent process
 * number, which renders exactly: "Nenhum registro encontrado conforme os
 * critérios selecionados."
 *
 * @author Rodrigo Prestes Machado
 */
class TjrsProcessScraperTest {

    private TjrsProcessScraper scraper;

    @BeforeEach
    void setUp() {
        scraper = new TjrsProcessScraper(new HtmlToMarkdown());
        scraper.notFoundMarkers = List.of("nenhum registro encontrado", "processo não encontrado",
                "nenhum processo foi localizado", "não foi possível localizar", "não localizado",
                "não foram encontrados processos para esse nome");
    }

    @Test
    void looksLikeNotFound_detectsRealTjrsNotFoundMessage() {
        String html = "<div>Nenhum registro encontrado conforme os critérios selecionados.</div>";

        assertTrue(scraper.looksLikeNotFound(html));
    }

    @Test
    void looksLikeNotFound_isCaseInsensitive() {
        String html = "<div>NENHUM REGISTRO ENCONTRADO conforme os critérios selecionados.</div>";

        assertTrue(scraper.looksLikeNotFound(html));
    }

    @Test
    void looksLikeNotFound_treatsBlankHtmlAsNotFound() {
        assertTrue(scraper.looksLikeNotFound(""));
        assertTrue(scraper.looksLikeNotFound(null));
    }

    @Test
    void looksLikeNotFound_returnsFalse_whenResultLooksLikeARealProcess() {
        String html = "<div>Número do Processo: 5033013-66.2026.8.21.0022</div>"
                + "<div>Comarca: Porto Alegre</div><div>Movimentações</div>";

        assertFalse(scraper.looksLikeNotFound(html));
    }

    @Test
    void looksLikeNotFound_detectsAnEmptyNameSearch() {
        String html = "<div>Não foram encontrados processos para esse nome com os critérios "
                + "informados.</div>";

        assertTrue(scraper.looksLikeNotFound(html));
    }

    @Test
    void hasZeroPartyResultCounts_detectsTheRealTjrsZeroResultsPage() {
        String html = "<h3>PARTES</h3><span class=\"mdc-tab__text-label\">"
                + "<span>RESULTADOS EM PROCESSOS EPROC</span>"
                + "<span hidden class=\"ng-star-inserted\">: <strong>0</strong></span></span>"
                + "<span class=\"mdc-tab__text-label\"><span>OUTROS RESULTADOS</span>"
                + "<span hidden class=\"ng-star-inserted\">: <strong>0</strong></span></span>";

        assertTrue(scraper.hasZeroPartyResultCounts(html));
    }

    @Test
    void hasZeroPartyResultCounts_returnsFalse_whenAnyCountIsNonZero() {
        String html = "<span>RESULTADOS EM PROCESSOS EPROC</span>"
                + "<span hidden>: <strong>3</strong></span>"
                + "<span>OUTROS RESULTADOS</span><span hidden>: <strong>0</strong></span>";

        assertFalse(scraper.hasZeroPartyResultCounts(html));
    }

    @Test
    void hasZeroPartyResultCounts_returnsFalse_whenCountsAreMissing() {
        assertFalse(scraper.hasZeroPartyResultCounts("<div>Número do Processo: 123</div>"));
        assertFalse(scraper.hasZeroPartyResultCounts(""));
        assertFalse(scraper.hasZeroPartyResultCounts(null));
    }

    @Test
    void resolvePartyHref_prefixesTheConsultaProcessualContext() {
        String pageUrl = "https://consulta.tjrs.jus.br/consulta-processual/partes/por-nome?nome=MARIA";

        assertEquals(
                "https://consulta.tjrs.jus.br/consulta-processual/partes/processos-por-nome?nome=MARIA",
                TjrsProcessScraper.resolvePartyHref(pageUrl,
                        "/partes/processos-por-nome?nome=MARIA"));
        assertEquals(
                "https://consulta.tjrs.jus.br/consulta-processual/partes/processos-por-nome?nome=MARIA",
                TjrsProcessScraper.resolvePartyHref(pageUrl,
                        "/consulta-processual/partes/processos-por-nome?nome=MARIA"));
        assertNull(TjrsProcessScraper.resolvePartyHref(pageUrl, " "));
    }
}
