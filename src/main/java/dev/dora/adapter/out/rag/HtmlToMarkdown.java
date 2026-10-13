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

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.regex.Pattern;

/**
 * Converts HTML content to Markdown using the flexmark-html2md library.
 *
 * @author Rodrigo Prestes Machado
 */
@ApplicationScoped
public class HtmlToMarkdown {

    /** Shared, thread-safe flexmark HTML-to-Markdown converter instance. */
    private static final FlexmarkHtmlConverter CONVERTER = FlexmarkHtmlConverter.builder().build();

    /** Matches a heading marker ({@code #} through {@code ######}) not at the start of a line. */
    private static final Pattern HEADING_INLINE = Pattern.compile("(?<!^|\\n)(#{1,6}\\s)");
    /** Matches an unordered list item marker ({@code * }) not at the start of a line. */
    private static final Pattern LIST_ITEM_INLINE = Pattern.compile("(?<!^|\\n)(\\*\\s)");
    /** Matches an escaped list item marker ({@code \* }) not at the start of a line. */
    private static final Pattern ESCAPED_LIST_INLINE = Pattern.compile("(?<!^|\\n)(\\\\\\*\\s)");
    /** Matches three or more consecutive newlines, used to collapse excessive blank lines. */
    private static final Pattern MULTIPLE_BLANKS = Pattern.compile("\\n{3,}");

    /**
     * Converts the given HTML string to Markdown.
     *
     * @param html the HTML content to convert
     * @return the Markdown representation, or empty string if conversion fails
     */
    public String toMarkdown(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        try {
            String markdown = CONVERTER.convert(html);
            return stripLeadingNavigationMenu(markdown);
        } catch (Exception e) {
            Log.warn("⚠️ Failed to convert HTML to Markdown", e);
            return "";
        }
    }

    /**
     * Removes the leading navigation/menu block from markdown. Content is considered to start
     * at the first H1-style heading, i.e. a line followed by a line of only '=' or '-'.
     *
     * @param markdown full markdown that may start with a menu list
     * @return markdown without the leading menu block, or original if no such block is found
     */
    public String stripLeadingNavigationMenu(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return markdown;
        }
        String[] lines = markdown.split("\n");
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if ((line.matches("^=+$") || line.matches("^-+$")) && line.length() >= 2) {
                StringBuilder sb = new StringBuilder();
                for (int j = i - 1; j < lines.length; j++) {
                    if (j > i - 1) {
                        sb.append('\n');
                    }
                    sb.append(lines[j]);
                }
                return sb.toString();
            }
        }
        return markdown;
    }

    /**
     * Normalizes markdown that may have been flattened into a single line.
     * Inserts line breaks before headings (# / ## / ### ...) and list items (* / \*).
     * Also un-escapes \* to * and \[ to [ / \] to ] produced by HTML to Markdown converters.
     *
     * @param markdown the markdown text to normalize
     * @return normalized markdown with proper line breaks
     */
    public String normalizeMarkdownLineBreaks(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return markdown;
        }
        String result = markdown;
        result = HEADING_INLINE.matcher(result).replaceAll("\n\n$1");
        result = ESCAPED_LIST_INLINE.matcher(result).replaceAll("\n$1");
        result = LIST_ITEM_INLINE.matcher(result).replaceAll("\n$1");
        result = result.replaceAll("(?m)^\\\\\\s*$", "");
        result = result.replace("\\*", "*");
        result = result.replace("\\[", "[");
        result = result.replace("\\]", "]");
        result = MULTIPLE_BLANKS.matcher(result).replaceAll("\n\n");
        return result.strip();
    }
}
