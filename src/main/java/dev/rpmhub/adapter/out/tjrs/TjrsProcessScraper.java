/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */
package dev.rpmhub.adapter.out.tjrs;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;

import dev.rpmhub.adapter.out.rag.HtmlToMarkdown;
import dev.rpmhub.adapter.out.tjrs.TjrsPartyPageParser.PersonRow;
import dev.rpmhub.adapter.out.tjrs.TjrsPartyPageParser.ProcessRow;
import dev.rpmhub.domain.model.PartySearchKind;
import dev.rpmhub.domain.model.PartySearchResult;
import dev.rpmhub.domain.model.ProcessLookupResult;
import dev.rpmhub.domain.port.out.ProcessLookupException;
import dev.rpmhub.domain.port.out.ProcessLookupPort;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Implementation of {@link ProcessLookupPort} that queries the public TJRS
 * ("Tribunal de Justiça do Rio Grande do Sul") process-lookup application at
 * {@code consulta.tjrs.jus.br/consulta-processual} using Playwright, and
 * converts the resulting HTML into clean Markdown.
 *
 * <p>The target page is an Angular single-page application (no server-rendered
 * results and no public documented API), which is why a real headless browser
 * (Playwright), rather than a plain HTTP client, is required here — see
 * {@link dev.rpmhub.adapter.out.rag.WebScraper} for the simpler HTTP-based
 * scraper used for RAG ingestion.
 *
 * <p><b>Deployment note:</b> the runtime environment must have Playwright's
 * browser binaries installed (e.g. {@code mvn com.microsoft.playwright:playwright:install}
 * or the equivalent {@code playwright install} step) in addition to this
 * library, plus the OS-level dependencies for headless Chromium
 * ({@code playwright install-deps} on Debian/Ubuntu). See the official
 * {@code mcr.microsoft.com/playwright/java} base image for containerized
 * deployments.
 *
 * <p><b>Fragility note:</b> the form field labels/selectors below reflect the
 * site's structure as observed at implementation time. Because the site's
 * layout may change, they are all externalized as configuration properties
 * (prefixed with {@code tjrs.scrape.}) so they can be adjusted without a code
 * change if TJRS updates its UI.
 *
 * @author Rodrigo Prestes Machado
 */
@ApplicationScoped
public class TjrsProcessScraper implements ProcessLookupPort {

    /**
     * Below this length, the result container's text is assumed to still be the empty
     * "loading" skeleton (just labels, no data) rather than an actual populated result.
     * See {@link #waitForResultContent(Page, double)}.
     */
    private static final int MIN_POPULATED_RESULT_LENGTH = 300;

    /** Polling interval used while waiting for the result to be populated. */
    private static final double RESULT_POLL_INTERVAL_MS = 300;

    /** How many paginator pages to walk on the name and process lists. */
    private static final int MAX_PARTY_PAGES = 10;

    /** Pause after changing page size or moving to the next page, so Angular can render. */
    private static final double PARTY_PAGE_SETTLE_MS = 400;

    /** Service that converts the scraped result HTML to clean Markdown. */
    private final HtmlToMarkdown htmlToMarkdownService;

    /** URL of the TJRS "consulta processual" single-page application. */
    @ConfigProperty(name = "tjrs.scrape.base-url", defaultValue = "https://consulta.tjrs.jus.br/consulta-processual/")
    String baseUrl;

    /** Maximum seconds to wait for the browser/page/navigation before timing out. */
    @ConfigProperty(name = "tjrs.scrape.timeout-seconds", defaultValue = "45")
    int timeoutSeconds;

    /** Whether to run Chromium headless. Keep {@code true} in production/containers. */
    @ConfigProperty(name = "tjrs.scrape.headless", defaultValue = "true")
    boolean headless;

    /** Accessible label/placeholder of the process-number input field. */
    @ConfigProperty(name = "tjrs.scrape.process-number-label", defaultValue = "Número Processo")
    String processNumberLabel;

    /** Accessible label of the court/jurisdiction ("TJ/Comarca") selector. */
    @ConfigProperty(name = "tjrs.scrape.comarca-label", defaultValue = "TJ/Comarca")
    String comarcaLabel;

    /** Accessible name of the search ("Pesquisar") button. */
    @ConfigProperty(name = "tjrs.scrape.search-button-label", defaultValue = "Pesquisar")
    String searchButtonLabel;

    /**
     * CSS selector scoping the result page's content area (the Angular router outlet content,
     * excluding the top toolbar, the page-specific "voltar/print" menu bar, and the footer).
     * Used to extract only the relevant result HTML instead of the whole page.
     */
    @ConfigProperty(name = "tjrs.scrape.result-selector", defaultValue = ".content")
    String resultSelector;

    /**
     * Lower-cased substrings that, if found in the result page, mean "process not found".
     * The default includes the exact message observed live on the TJRS results page
     * ("Nenhum registro encontrado conforme os critérios selecionados."), plus a few
     * plausible variants as a safety margin against minor copy changes.
     */
    @ConfigProperty(name = "tjrs.scrape.not-found-markers", defaultValue = "nenhum registro encontrado,"
            + "processo não encontrado,nenhum processo foi localizado,não foi possível localizar,não localizado,"
            + "não foram encontrados processos para esse nome")
    List<String> notFoundMarkers;

    /** Maximum people listed when several names match and the client must choose. */
    @ConfigProperty(name = "tjrs.scrape.people-result-limit", defaultValue = "15")
    int peopleResultLimit;

    /** Maximum active processes listed for the chosen person. */
    @ConfigProperty(name = "tjrs.scrape.process-list-limit", defaultValue = "20")
    int processListLimit;

    /** Material paginator page size requested on the name and process tables. */
    @ConfigProperty(name = "tjrs.scrape.party-page-size", defaultValue = "50")
    int partyPageSize;

    /** Reads the name and process tables from the TJRS party-search HTML. */
    private final TjrsPartyPageParser partyPageParser = new TjrsPartyPageParser();

    /**
     * Creates a TjrsProcessScraper with the given HTML-to-Markdown service.
     *
     * @param htmlToMarkdownService service for converting HTML to Markdown
     */
    @Inject
    public TjrsProcessScraper(HtmlToMarkdown htmlToMarkdownService) {
        this.htmlToMarkdownService = htmlToMarkdownService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<ProcessLookupResult> lookup(String cnjNumber) {
        double timeoutMs = Math.max(1, timeoutSeconds) * 1000d;

        try (Playwright playwright = Playwright.create()) {
            try (Browser browser = openBrowser(playwright)) {

                BrowserContext context = browser.newContext();
                Page page = context.newPage();
                page.setDefaultTimeout(timeoutMs);

                // DOMCONTENTLOADED: the TJRS SPA can keep the "load" event from firing
                // (analytics/hCaptcha). Waiting for "load" then times out after 45s.
                page.navigate(baseUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                page.getByLabel(processNumberLabel).first().waitFor();

                page.getByLabel(processNumberLabel).first().fill(cnjNumber);

                Locator searchButton = page.getByRole(AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName(searchButtonLabel)).first();
                confirmComarcaSelection(page, searchButton);
                dismissComarcaOverlay(page);

                // Native DOM click: Playwright's regular click times out while the Angular CDK
                // overlay (backdrop / mat-option) is still intercepting pointer events.
                searchButton.evaluate("button => button.click()");
                waitForResultContent(page, timeoutMs);

                String html = extractResultHtml(page);

                if (looksLikeNotFound(html)) {
                    Log.info("ℹ️ Processo " + cnjNumber + " não localizado no TJRS");
                    return Optional.empty();
                }

                String markdown = htmlToMarkdownService.normalizeMarkdownLineBreaks(
                        htmlToMarkdownService.toMarkdown(html));

                if (markdown.isBlank()) {
                    throw new ProcessLookupException(
                            "Conversão para Markdown resultou em conteúdo vazio para o processo " + cnjNumber);
                }

                return Optional.of(new ProcessLookupResult(cnjNumber, markdown, page.url()));
            }
        } catch (ProcessLookupException e) {
            throw e;
        } catch (PlaywrightException e) {
            throw new ProcessLookupException(
                    "Falha de automação (Playwright) ao consultar o processo " + cnjNumber + " no TJRS", e);
        } catch (Exception e) {
            throw new ProcessLookupException(
                    "Erro inesperado ao consultar o processo " + cnjNumber + " no TJRS", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PartySearchResult searchByPerson(String normalizedName) {
        double timeoutMs = Math.max(1, timeoutSeconds) * 1000d;

        try (Playwright playwright = Playwright.create()) {
            try (Browser browser = openBrowser(playwright)) {
                BrowserContext context = browser.newContext();
                Page page = context.newPage();
                page.setDefaultTimeout(timeoutMs);

                List<PersonRow> people = searchPeople(page, normalizedName, "A", timeoutMs);
                boolean pageListedSomeone = !people.isEmpty();
                people = partyPageParser.exactNames(people, normalizedName);
                if (people.isEmpty()) {
                    Log.info("ℹ️ Nenhum processo ativo para o nome exato " + normalizedName
                            + "; consultando processos baixados no TJRS");
                    List<PersonRow> closedCases = searchPeople(page, normalizedName, "B", timeoutMs);
                    pageListedSomeone = pageListedSomeone || !closedCases.isEmpty();
                    people = partyPageParser.exactNames(closedCases, normalizedName);
                }
                if (people.isEmpty()) {
                    if (pageListedSomeone || looksLikeNotFound(extractResultHtml(page))) {
                        Log.info("ℹ️ Nenhuma pessoa localizada no TJRS para o nome exato " + normalizedName);
                        return new PartySearchResult(PartySearchKind.NOT_FOUND, "", page.url());
                    }
                    throw new ProcessLookupException(
                            "A página de nomes do TJRS não trouxe pessoas para " + normalizedName);
                }

                if (people.size() > 1) {
                    return new PartySearchResult(PartySearchKind.AMBIGUOUS_PEOPLE,
                            partyPageParser.peopleMarkdown(people, peopleResultLimit), page.url());
                }

                return openProcessList(page, people.get(0), timeoutMs);
            }
        } catch (ProcessLookupException e) {
            throw e;
        } catch (PlaywrightException e) {
            throw new ProcessLookupException(
                    "Falha de automação (Playwright) ao consultar processos de " + normalizedName + " no TJRS", e);
        } catch (Exception e) {
            throw new ProcessLookupException(
                    "Erro inesperado ao consultar processos de " + normalizedName + " no TJRS", e);
        }
    }

    /**
     * Opens Chromium with the flags required to run inside Docker.
     *
     * @param playwright the Playwright instance that owns the browser
     * @return a launched Chromium browser; the caller closes it
     */
    private Browser openBrowser(Playwright playwright) {
        return playwright.chromium()
                .launch(new BrowserType.LaunchOptions()
                        .setHeadless(headless)
                        // Required inside Docker: Chromium's sandbox and the default 64 MiB
                        // /dev/shm otherwise hang the first navigation until timeout.
                        .setArgs(List.of(
                                "--no-sandbox",
                                "--disable-dev-shm-usage",
                                "--disable-gpu")));
    }

    /**
     * Opens the name-search page and reads the people table.
     *
     * @param page           current Playwright page
     * @param normalizedName upper-case person name
     * @param situacao       {@code A} for active cases, {@code B} for closed ones
     * @param timeoutMs      navigation timeout
     * @return distinct people found on that search, before the exact-name filter
     */
    private List<PersonRow> searchPeople(Page page, String normalizedName, String situacao, double timeoutMs) {
        page.navigate(partySearchUrl(normalizedName, situacao),
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        waitForPartyContent(page, timeoutMs);
        selectPartyPageSize(page);
        return collectPeople(page);
    }

    /**
     * Builds the URL of the name-search results page.
     *
     * <p>{@code tipoPesquisa=E} is the court's exact-name search. The public form
     * only offers phonetic match when no comarca is selected, and that match
     * returns similar names instead of the name the client typed.
     *
     * @param normalizedName upper-case person name
     * @param situacao       {@code A} for active cases, {@code B} for closed ones
     * @return absolute URL of {@code /partes/por-nome}
     */
    private String partySearchUrl(String normalizedName, String situacao) {
        String base = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        return base + "partes/por-nome?nome=" + URLEncoder.encode(normalizedName, StandardCharsets.UTF_8)
                + "&comarca=&tipoPesquisa=E&situacao=" + situacao + "&movimentados=0";
    }

    /**
     * Resolves a link from the party pages against the site origin.
     *
     * <p>Angular may emit a root-relative path such as {@code /partes/processos-por-nome}.
     * Those paths belong under {@code /consulta-processual}.
     *
     * @param pageUrl URL of the page that contained the link
     * @param href    raw href attribute
     * @return absolute URL, or {@code null} when {@code href} is blank
     */
    static String resolvePartyHref(String pageUrl, String href) {
        if (href == null || href.isBlank()) {
            return null;
        }
        if (href.startsWith("http://") || href.startsWith("https://")) {
            return href;
        }
        URI current = URI.create(pageUrl);
        String origin = current.getScheme() + "://" + current.getAuthority();
        if (href.startsWith("/")) {
            if (href.startsWith("/consulta-processual/") || href.startsWith("/consulta-processual?")) {
                return origin + href;
            }
            return origin + "/consulta-processual" + href;
        }
        return current.resolve(href).toString();
    }

    private PartySearchResult openProcessList(Page page, PersonRow person, double timeoutMs) {
        String href = resolvePartyHref(page.url(), person.href());
        if (href == null) {
            throw new ProcessLookupException("A pessoa " + person.name() + " não tem link para a lista de processos");
        }
        page.navigate(href, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        waitForPartyContent(page, timeoutMs);
        selectPartyPageSize(page);

        List<ProcessRow> processes = collectProcesses(page);
        if (processes.isEmpty()) {
            Log.info("ℹ️ Nenhum processo ativo localizado no TJRS para " + person.name());
            return new PartySearchResult(PartySearchKind.NOT_FOUND, "", page.url());
        }
        String situation = page.url().contains("situacao=B") ? "baixados" : "ativos";
        String markdown = partyPageParser.processesMarkdown(processes, processListLimit)
                + "\n\nSituação consultada: " + situation + ".";
        return new PartySearchResult(PartySearchKind.PROCESS_LIST, markdown, page.url());
    }

    private List<PersonRow> collectPeople(Page page) {
        List<PersonRow> raw = new ArrayList<>();
        int limit = Math.max(1, peopleResultLimit);
        for (int pageIndex = 0; pageIndex < MAX_PARTY_PAGES; pageIndex++) {
            raw.addAll(partyPageParser.parsePeople(extractResultHtml(page)));
            List<PersonRow> distinct = partyPageParser.distinctPeople(raw);
            if (distinct.size() > limit) {
                return distinct;
            }
            if (!advancePartyPaginators(page)) {
                return distinct;
            }
        }
        return partyPageParser.distinctPeople(raw);
    }

    private List<ProcessRow> collectProcesses(Page page) {
        List<ProcessRow> processes = new ArrayList<>();
        int limit = Math.max(1, processListLimit);
        for (int pageIndex = 0; pageIndex < MAX_PARTY_PAGES; pageIndex++) {
            for (ProcessRow row : partyPageParser.parseProcesses(extractResultHtml(page))) {
                if (processes.stream().noneMatch(existing -> existing.number().equals(row.number()))) {
                    processes.add(row);
                }
            }
            if (processes.size() > limit || !advancePartyPaginators(page)) {
                return processes;
            }
        }
        return processes;
    }

    /**
     * Waits until the party page shows a person/process link or a not-found message.
     *
     * @param page      the current Playwright page
     * @param timeoutMs maximum time to wait
     */
    private void waitForPartyContent(Page page, double timeoutMs) {
        Locator result = page.locator(resultSelector).first();
        long deadline = System.currentTimeMillis() + (long) timeoutMs;

        while (System.currentTimeMillis() < deadline) {
            if (result.count() > 0) {
                String text = result.innerText();
                boolean hasLink = result.locator("a[href*='processos-por-nome'], a[href*='processo/resumo']").count() > 0;
                if (hasLink || looksLikeNotFound(text)) {
                    return;
                }
            }
            page.waitForTimeout(RESULT_POLL_INTERVAL_MS);
        }
    }

    /**
     * Asks every visible Material paginator to show {@link #partyPageSize} rows.
     *
     * <p>A paginator that already lists every row is left as it is. The control is a
     * Material select: a normal Playwright click waits out the page timeout because
     * the CDK overlay backdrop covers the option. A native click selects it. A failure
     * keeps the site's default page size and the search continues with the next button.
     *
     * @param page the current Playwright page
     */
    private void selectPartyPageSize(Page page) {
        String sizeLabel = String.valueOf(Math.max(1, partyPageSize));
        int count = page.locator("mat-paginator").count();
        for (int i = 0; i < count; i++) {
            boolean opened = false;
            try {
                opened = openPageSizeSelect(page, i, sizeLabel);
                if (!opened) {
                    continue;
                }
                page.waitForTimeout(PARTY_PAGE_SETTLE_MS);
                if (!choosePageSizeOption(page, sizeLabel)) {
                    Log.warn("⚠️ Não foi possível ajustar a paginação da consulta por nome no TJRS");
                }
                page.waitForTimeout(PARTY_PAGE_SETTLE_MS);
            } catch (PlaywrightException e) {
                Log.warn("⚠️ Não foi possível ajustar a paginação da consulta por nome no TJRS");
            } finally {
                if (opened) {
                    dismissComarcaOverlay(page);
                }
            }
        }
    }

    /**
     * Opens the page-size select of one paginator when a larger page would show more rows.
     *
     * @param page      the current Playwright page
     * @param index     position of the {@code mat-paginator} on the page
     * @param sizeLabel option text to select, such as {@code 50}
     * @return {@code true} when the dropdown was opened
     */
    private boolean openPageSizeSelect(Page page, int index, String sizeLabel) {
        Object opened = page.evaluate(
                """
                ([index, sizeLabel]) => {
                    const paginator = document.querySelectorAll('mat-paginator')[index];
                    if (!paginator) {
                        return false;
                    }
                    const select = paginator.querySelector('mat-select');
                    if (!select || select.getClientRects().length === 0) {
                        return false;
                    }
                    const current = (select.innerText || '').replace(/\\s+/g, ' ').trim();
                    if (current === sizeLabel) {
                        return false;
                    }
                    const range = paginator.querySelector('.mat-mdc-paginator-range-label');
                    const match = range && /de\\s+(\\d+)/.exec(range.innerText || '');
                    const shown = parseInt(current, 10);
                    if (match && shown && Number(match[1]) <= shown) {
                        return false;
                    }
                    select.click();
                    return true;
                }
                """,
                List.of(index, sizeLabel));
        return Boolean.TRUE.equals(opened);
    }

    /**
     * Clicks the open page-size option whose text equals {@code sizeLabel}.
     *
     * @param page      the current Playwright page
     * @param sizeLabel option text, such as {@code 50}
     * @return {@code true} when the option was clicked
     */
    private boolean choosePageSizeOption(Page page, String sizeLabel) {
        Object selected = page.evaluate(
                """
                (sizeLabel) => {
                    const option = [...document.querySelectorAll('mat-option')]
                        .find(item => (item.innerText || '').trim() === sizeLabel);
                    if (!option) {
                        return false;
                    }
                    option.click();
                    return true;
                }
                """,
                sizeLabel);
        return Boolean.TRUE.equals(selected);
    }

    /**
     * Clicks every enabled "next page" button on the party tables.
     *
     * @param page the current Playwright page
     * @return {@code true} when at least one paginator advanced
     */
    private boolean advancePartyPaginators(Page page) {
        Locator nextButtons = page.locator("mat-paginator button.mat-mdc-paginator-navigation-next");
        boolean clicked = false;
        int count = nextButtons.count();
        for (int i = 0; i < count; i++) {
            Locator button = nextButtons.nth(i);
            try {
                if (button.isEnabled()) {
                    button.click();
                    clicked = true;
                }
            } catch (PlaywrightException e) {
                Log.warn("⚠️ Não foi possível avançar a página da consulta por nome no TJRS");
            }
        }
        if (clicked) {
            page.waitForTimeout(PARTY_PAGE_SETTLE_MS);
        }
        return clicked;
    }

    /**
     * Waits until the result view actually has its data rendered, instead of just having
     * navigated there.
     *
     * <p>Confirmed live: {@link LoadState#NETWORKIDLE} alone is not a reliable signal here.
     * This Angular SPA routes to the result view client-side and renders its header/summary
     * components immediately (empty), fetching the actual process data (or the "not found"
     * message) asynchronously afterwards; the network can appear idle for the brief instant
     * between that route change and the data-fetch request actually starting, causing
     * {@code waitForLoadState(NETWORKIDLE)} to resolve right away against the still-empty
     * skeleton. Polling {@link #resultSelector}'s text until it either matches a "not found"
     * marker or grows past a trivial skeleton length avoids that race.
     *
     * @param page      the current Playwright page, just after submitting the search
     * @param timeoutMs maximum time to wait before giving up (falls back to whatever HTML is
     *                  present, so {@link #extractResultHtml} / {@link #looksLikeNotFound} can
     *                  still make a best effort, or a clear error surfaces downstream)
     */
    private void waitForResultContent(Page page, double timeoutMs) {
        Locator result = page.locator(resultSelector).first();
        long deadline = System.currentTimeMillis() + (long) timeoutMs;

        while (System.currentTimeMillis() < deadline) {
            if (result.count() > 0) {
                String text = result.innerText();
                if (text != null && (looksLikeNotFound(text) || text.trim().length() > MIN_POPULATED_RESULT_LENGTH)) {
                    return;
                }
            }
            page.waitForTimeout(RESULT_POLL_INTERVAL_MS);
        }
    }

    /**
     * Confirms the "TJ/Comarca" selection, which the search form requires to be "touched"
     * before the "Pesquisar" button is enabled.
     *
     * <p>The site itself already auto-detects and pre-selects the correct comarca from the
     * CNJ process number as soon as it is typed (its 7-digit court/jurisdiction segment
     * encodes the comarca) — the dropdown just needs to be opened and closed to mark the
     * field as touched, without forcing the broader, unrelated first option in the list;
     * doing the latter would search the wrong jurisdiction and yield false "not found" results.
     *
     * <p>Whether a comarca was actually auto-detected is confirmed indirectly, via
     * {@code searchButton}'s enabled state after opening the dropdown — this is more reliable
     * than asserting the option's ARIA "selected" state directly, since Angular Material may
     * not expose it the way a strict ARIA role query expects. If the button is still disabled
     * (e.g. a legacy "Themis"-format number with no CNJ jurisdiction segment to auto-detect
     * from), the first (broadest) option is explicitly clicked as a fallback.
     *
     * @param page         the current Playwright page, positioned on the search form
     * @param searchButton locator for the "Pesquisar" button, used to confirm the field is valid
     */
    private void confirmComarcaSelection(Page page, Locator searchButton) {
        page.getByLabel(comarcaLabel).first().click();

        if (!searchButton.isEnabled()) {
            page.getByRole(AriaRole.OPTION).first().click();
        }
    }

    /**
     * Dismisses the Angular CDK overlay opened by the "TJ/Comarca" dropdown.
     *
     * <p>Confirmed live with Playwright's headless Chromium: after opening the dropdown,
     * {@code Escape} and clicking the backdrop do not reliably detach the overlay. The
     * leftover {@code .cdk-overlay-backdrop} and {@code mat-option} keep intercepting
     * pointer events, so Playwright's regular click on "Pesquisar" retries until timeout.
     * Clearing the overlay container from the DOM is the deterministic close; the search
     * is then submitted with a native {@code HTMLElement.click()} that does not require
     * the button to pass Playwright's actionability checks.
     *
     * @param page the current Playwright page
     */
    private void dismissComarcaOverlay(Page page) {
        page.keyboard().press("Escape");
        page.evaluate("""
                () => {
                    document.querySelectorAll('.cdk-overlay-container').forEach(container => {
                        container.innerHTML = '';
                    });
                }
                """);
    }

    /**
     * Extracts only the result HTML from the page, scoped by {@link #resultSelector}, so the
     * conversion to Markdown does not include the top toolbar, the page-specific "voltar/print"
     * menu bar, or the footer/hCaptcha disclaimer — all of which are otherwise present on every
     * page of this Angular application and would pollute the Markdown returned to the AI agent.
     *
     * <p>Falls back to the full page HTML if the selector matches nothing, so a change in the
     * site's structure degrades gracefully instead of losing the result entirely.
     *
     * @param page the current Playwright page, positioned on the result page
     * @return the scoped (or, as a fallback, full) result HTML
     */
    private String extractResultHtml(Page page) {
        Locator result = page.locator(resultSelector).first();
        if (result.count() > 0) {
            return result.innerHTML();
        }
        Log.warn("⚠️ Result selector '" + resultSelector + "' not found on the TJRS page; "
                + "falling back to the full page content");
        return page.content();
    }

    /**
     * Heuristically checks whether the result HTML indicates that no process was found,
     * based on {@link #notFoundMarkers}.
     *
     * @param html the raw result page HTML
     * @return {@code true} if any configured "not found" marker is present
     */
    boolean looksLikeNotFound(String html) {
        if (html == null || html.isBlank()) {
            return true;
        }
        String normalized = html.toLowerCase();
        for (String marker : notFoundMarkers) {
            if (marker != null && !marker.isBlank() && normalized.contains(marker.toLowerCase().trim())) {
                return true;
            }
        }
        return false;
    }
}
