package dev.rpmhub.adapter.out.tjrs;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.rpmhub.adapter.out.rag.HtmlToMarkdown;

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
                "nenhum processo foi localizado", "não foi possível localizar", "não localizado");
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
}
