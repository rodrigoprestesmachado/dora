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
            + "processo não encontrado,nenhum processo foi localizado,não foi possível localizar,não localizado")
    List<String> notFoundMarkers;

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
            try (Browser browser = playwright.chromium()
                    .launch(new BrowserType.LaunchOptions()
                            .setHeadless(headless)
                            // Required inside Docker: Chromium's sandbox and the default 64 MiB
                            // /dev/shm otherwise hang the first navigation until timeout.
                            .setArgs(List.of(
                                    "--no-sandbox",
                                    "--disable-dev-shm-usage",
                                    "--disable-gpu")))) {

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
