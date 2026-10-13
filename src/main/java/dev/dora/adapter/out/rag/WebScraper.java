/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */

package dev.dora.adapter.out.rag;

import dev.dora.domain.model.DocumentData;
import dev.dora.domain.port.out.WebScraperPort;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Implementation of {@link WebScraperPort} that fetches HTML from URLs and converts to
 * Markdown documents.
 *
 * @author Rodrigo Prestes Machado
 */
@ApplicationScoped
public class WebScraper implements WebScraperPort {

    /** Service that converts raw HTML responses to clean Markdown text. */
    private final HtmlToMarkdown htmlToMarkdownService;
    /** Blocking HTTP client used to fetch pages; follow-redirects is enabled. */
    private final HttpClient httpClient;

    /** Maximum seconds to wait for a connection or response before timing out. */
    @ConfigProperty(name = "rag.scrape.timeout-seconds", defaultValue = "30")
    int timeoutSeconds;

    /** {@code User-Agent} header sent with every scrape request. */
    @ConfigProperty(name = "rag.scrape.user-agent", defaultValue = "Dora-RAG-Scraper/1.0")
    String userAgent;

    /** When {@code true},
            scraped Markdown is also saved to {@link #markdownOutputDir} for inspection. */
    @ConfigProperty(name = "rag.scrape.save-markdown", defaultValue = "false")
    boolean saveMarkdownToDisk;

    /**
     * Directory where scraped Markdown files are saved when {@link #saveMarkdownToDisk} is
     * {@code true}.
     */
    @ConfigProperty(name = "rag.scrape.save-markdown.dir", defaultValue = "target/scraped-markdown")
    String markdownOutputDir;

    /**
     * Creates a WebScraper with the given HTML-to-Markdown service.
     * Initialises the HTTP client using the configured timeout.
     *
     * @param htmlToMarkdownService service for converting HTML to Markdown
     */
    @Inject
    public WebScraper(HtmlToMarkdown htmlToMarkdownService) {
        this.htmlToMarkdownService = htmlToMarkdownService;
        int timeout = timeoutSeconds <= 0 ? 30 : timeoutSeconds;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeout))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Fetches the URL, converts HTML to Markdown, and returns a Document with source metadata.
     *
     * @param url the URL to scrape
     * @return Optional containing the Document, or empty if fetch/conversion failed
     */
    @Override
    public Optional<DocumentData> scrapeToDocument(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        try {
            int timeout = timeoutSeconds <= 0 ? 30 : timeoutSeconds;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeout))
                    .header("User-Agent", userAgent)
                    .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                Log.warn("⚠️ Scrape failed for " + url + ": HTTP " + response.statusCode());
                return Optional.empty();
            }

            String body = response.body();
            String markdown = toMarkdownFromResponse(url, body);
            if (markdown.isBlank()) {
                Log.warn("⚠️ Empty content or conversion failed for " + url);
                return Optional.empty();
            }

            persistMarkdownIfEnabled(url, markdown);

            return Optional.of(new DocumentData(markdown, url));
        } catch (Exception e) {
            Log.warn("❌ Error scraping " + url, e);
            return Optional.empty();
        }
    }

    /**
     * Produces markdown from the HTTP response body. If the URL ends with .md and the body
     * is not HTML, treats the body as raw markdown (strips nav menu + normalizes line breaks).
     * Otherwise converts HTML to markdown and strips the menu.
     */
    private String toMarkdownFromResponse(String url, String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        boolean urlEndsWithMd = url != null && url.trim().toLowerCase().endsWith(".md");
        boolean bodyLooksLikeHtml = body.trim().startsWith("<");
        if (urlEndsWithMd && !bodyLooksLikeHtml) {
            String md = htmlToMarkdownService.stripLeadingNavigationMenu(body);
            return htmlToMarkdownService.normalizeMarkdownLineBreaks(md);
        }
        String md = htmlToMarkdownService.toMarkdown(body);
        return htmlToMarkdownService.normalizeMarkdownLineBreaks(md);
    }

    /**
     * Writes the scraped Markdown to disk under {@link #markdownOutputDir} when saving is enabled.
     * Uses a safe sanitised filename derived from the URL.
     *
     * @param url      source URL (used to derive a filename)
     * @param markdown Markdown content to persist
     */
    private void persistMarkdownIfEnabled(String url, String markdown) {
        if (!saveMarkdownToDisk) {
            return;
        }
        try {
            Path dir = Path.of(markdownOutputDir);
            Files.createDirectories(dir);

            String safeFileName = toSafeFileName(url) + ".md";
            Path file = dir.resolve(safeFileName);

            Files.writeString(file, markdown, StandardCharsets.UTF_8);
            Log.debug("💾 Saved scraped markdown for " + url + " to " + file);
        } catch (Exception e) {
            Log.warn("⚠️ Failed to persist markdown for " + url, e);
        }
    }

    /**
     * Clears all existing markdown files from the output directory when saving is enabled.
     * Used at the start of a scrape ingestion run to avoid mixing old and new files.
     */
    @Override
    public void clearMarkdownOutputDirIfEnabled() {
        if (!saveMarkdownToDisk) {
            return;
        }
        try {
            Path dir = Path.of(markdownOutputDir);
            if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                return;
            }
            try (Stream<Path> paths = Files.walk(dir)) {
                paths.filter(Files::isRegularFile).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception e) {
                        Log.warn("⚠️ Failed to delete old markdown file " + path, e);
                    }
                });
            }
            Log.debug("🧹 Cleared existing markdown files from directory " + dir);
        } catch (Exception e) {
            Log.warn("⚠️ Failed to clear markdown output directory " + markdownOutputDir, e);
        }
    }

    /**
     * Derives a safe file-system name from a URL by stripping the scheme, replacing non-ASCII
     * characters and appending a timestamp to avoid collisions.
     *
     * @param url source URL to sanitise
     * @return a file-safe name suitable for use as a Markdown filename (without extension)
     */
    private String toSafeFileName(String url) {
        String sanitized = url
                .replaceFirst("^https?://", "")
                .replaceAll("[^a-zA-Z0-9-_\\.]", "_");
        if (sanitized.length() > 100) {
            sanitized = sanitized.substring(0, 100);
        }
        if (sanitized.isBlank()) {
            sanitized = "document";
        }
        return sanitized + "_" + System.currentTimeMillis();
    }
}
