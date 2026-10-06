package dev.rpmhub.adapter.out.tjrs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the TJRS name-search HTML parser.
 *
 * @author Rodrigo Prestes Machado
 */
class TjrsPartyPageParserTest {

    private static final String PEOPLE_HTML = """
            <table>
              <tr><th>NOME</th><th>CPF/CNPJ</th></tr>
              <tr>
                <td><a href="/partes/processos-por-nome?parteSelecionadaNome=MARIA%20SOUZA&amp;parteSelecionadaCpfCnpj=52998224725&amp;parteSelecionadaFrom=eproc">MARIA SOUZA</a></td>
                <td><p>529.982.247-25</p></td>
              </tr>
              <tr>
                <td><a href="/partes/processos-por-nome?parteSelecionadaNome=MARIA%20SOUZA&amp;parteSelecionadaFrom=legado">MARIA SOUZA</a></td>
                <td><p>CPF/CNPJ não informado</p></td>
              </tr>
              <tr>
                <td><a href="/partes/processos-por-nome?parteSelecionadaNome=MARIA%20SOUZA%20SILVA&amp;parteSelecionadaCpfCnpj=39053344705&amp;parteSelecionadaFrom=eproc">MARIA SOUZA SILVA</a></td>
                <td><p>390.533.447-05</p></td>
              </tr>
            </table>
            """;

    private static final String PROCESS_HTML = """
            <table aria-label="Processos">
              <tr>
                <th>Número Themis/CNJ</th><th>Parte</th><th>Comarca</th>
                <th>Classe CNJ</th><th>Movimento</th><th>Última Movimentação</th>
              </tr>
              <tr>
                <td><a href="/processo/resumo?numero=5033013-66.2026.8.21.0022">5033013-66.2026.8.21.0022</a></td>
                <td><p>MARIA | SOUZA</p></td>
                <td><p>Porto Alegre</p></td>
                <td><p>Procedimento Comum</p></td>
                <td><p>Ativo</p></td>
                <td><p>01/01/2026</p></td>
              </tr>
            </table>
            """;

    private TjrsPartyPageParser parser;

    @BeforeEach
    void setUp() {
        parser = new TjrsPartyPageParser();
    }

    @Test
    void distinctPeople_collapsesTheLegadoRowWithoutCpfAndKeepsTheEprocLink() {
        List<TjrsPartyPageParser.PersonRow> people = parser.distinctPeople(parser.parsePeople(PEOPLE_HTML));

        assertEquals(2, people.size());
        TjrsPartyPageParser.PersonRow maria = people.get(0);
        assertEquals("MARIA SOUZA", maria.name());
        assertEquals("52998224725", maria.cpfDigits());
        assertTrue(maria.fromEproc());
        assertTrue(maria.href().contains("parteSelecionadaFrom=eproc"));
        assertEquals("MARIA SOUZA SILVA", people.get(1).name());
    }

    @Test
    void exactNames_keepsOnlyTheNameTheClientTyped() {
        List<TjrsPartyPageParser.PersonRow> people = parser.distinctPeople(parser.parsePeople(PEOPLE_HTML));

        List<TjrsPartyPageParser.PersonRow> exact = parser.exactNames(people, "MARIA SOUZA");

        assertEquals(1, exact.size());
        assertEquals("MARIA SOUZA", exact.get(0).name());
        assertTrue(parser.exactNames(people, "JOAO SILVA").isEmpty());
    }

    @Test
    void processesMarkdown_includesTheNumberAndEscapesPipes() {
        String markdown = parser.processesMarkdown(parser.parseProcesses(PROCESS_HTML), 20);

        assertTrue(markdown.contains("5033013-66.2026.8.21.0022"));
        assertTrue(markdown.contains("MARIA \\| SOUZA"));
        assertTrue(markdown.contains("Porto Alegre"));
        assertTrue(markdown.contains("Procedimento Comum"));
        assertTrue(markdown.contains("01/01/2026"));
    }

    @Test
    void peopleMarkdown_notesWhenTheListIsTruncated() {
        List<TjrsPartyPageParser.PersonRow> people = parser.distinctPeople(parser.parsePeople(PEOPLE_HTML));

        String markdown = parser.peopleMarkdown(people, 1);

        assertTrue(markdown.contains("MARIA SOUZA"));
        assertFalse(markdown.contains("MARIA SOUZA SILVA"));
        assertTrue(markdown.contains("Há mais pessoas"));
    }

    @Test
    void parsePeople_keepsTheVisibleDigitsOfAMaskedDocument() {
        String html = """
                <table>
                  <tr>
                    <td><a href="/partes/processos-por-nome?parteSelecionadaNome=MACIEL%20MIRANDA%20PEREIRA&amp;parteSelecionadaCpfCnpj=039.9**.***-**&amp;parteSelecionadaFrom=eproc">MACIEL MIRANDA PEREIRA</a></td>
                    <td><p>039.9**.***-**</p></td>
                  </tr>
                </table>
                """;

        List<TjrsPartyPageParser.PersonRow> people = parser.distinctPeople(parser.parsePeople(html));

        assertEquals("0399", people.get(0).cpfDigits());
        assertEquals(1, parser.exactNames(people, "MACIEL MIRANDA PEREIRA").size());
    }

    @Test
    void parsePeople_returnsEmptyForBlankHtml() {
        assertTrue(parser.parsePeople("").isEmpty());
        assertTrue(parser.parseProcesses(null).isEmpty());
    }
}
