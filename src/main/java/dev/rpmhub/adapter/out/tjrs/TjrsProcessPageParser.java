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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Trims the TJRS process-summary HTML so the movements table keeps a single row.
 *
 * <p>The resumo page renders every movement inside {@code app-movimentos-resumo}.
 * The row kept is the one with the latest date ({@code dd/MM/yyyy}, optionally
 * with a time). A tie goes to the highest event number. When no row has a date,
 * the first data row stays, because that summary lists recent movements first.
 *
 * @author Rodrigo Prestes Machado
 */
class TjrsProcessPageParser {

    private static final Pattern COMPONENT = Pattern.compile(
            "(?is)<app-movimentos-resumo\\b[^>]*>.*?</app-movimentos-resumo>");
    private static final Pattern TABLE_TAG = Pattern.compile("(?i)</?table\\b[^>]*>");
    private static final Pattern ROW = Pattern.compile("(?is)<tr\\b[^>]*>.*?</tr>");
    private static final Pattern CELL = Pattern.compile("(?is)<td\\b[^>]*>(.*?)</td>");
    private static final Pattern TAG = Pattern.compile("(?is)<[^>]+>");
    private static final Pattern DATE_CELL = Pattern.compile(
            "^(\\d{2}/\\d{2}/\\d{4})(?:\\s+(\\d{2}:\\d{2}(?::\\d{2})?))?$");
    private static final Pattern EVENT_CELL = Pattern.compile("^\\d+$");

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter TIME_SHORT = DateTimeFormatter.ofPattern("HH:mm")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter TIME_LONG = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withResolverStyle(ResolverStyle.STRICT);

    /**
     * Returns {@code html} with each movements table reduced to its latest row.
     *
     * <p>HTML without {@code app-movimentos-resumo}, or a table that already has
     * at most one data row, is returned unchanged.
     *
     * @param html result HTML scraped from the process page
     * @return the same HTML with only the latest movement in each summary table
     */
    String keepLatestMovement(String html) {
        if (html == null || html.isBlank()) {
            return html;
        }
        Matcher matcher = COMPONENT.matcher(html);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(trimComponent(matcher.group())));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String trimComponent(String componentHtml) {
        int[] span = outermostTable(componentHtml);
        if (span == null) {
            return componentHtml;
        }
        String table = componentHtml.substring(span[0], span[1]);
        String trimmed = trimTable(table);
        if (trimmed.equals(table)) {
            return componentHtml;
        }
        return componentHtml.substring(0, span[0]) + trimmed + componentHtml.substring(span[1]);
    }

    private static String trimTable(String tableHtml) {
        List<Row> rows = rows(tableHtml);
        List<Integer> dataRows = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).data()) {
                dataRows.add(i);
            }
        }
        if (dataRows.size() <= 1) {
            return tableHtml;
        }
        int keep = latest(rows, dataRows);
        StringBuilder out = new StringBuilder();
        int cursor = 0;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).data() && i != keep) {
                Row row = rows.get(i);
                out.append(tableHtml, cursor, row.start());
                cursor = row.end();
            }
        }
        out.append(tableHtml, cursor, tableHtml.length());
        return out.toString();
    }

    private static int latest(List<Row> rows, List<Integer> dataRows) {
        int best = dataRows.get(0);
        for (int index : dataRows) {
            if (isLater(rows.get(index), rows.get(best))) {
                best = index;
            }
        }
        return best;
    }

    private static boolean isLater(Row candidate, Row current) {
        if (candidate.when() == null) {
            return false;
        }
        if (current.when() == null) {
            return true;
        }
        int byDate = candidate.when().compareTo(current.when());
        if (byDate != 0) {
            return byDate > 0;
        }
        return candidate.eventNumber() > current.eventNumber();
    }

    private static List<Row> rows(String tableHtml) {
        List<Row> rows = new ArrayList<>();
        Matcher matcher = ROW.matcher(tableHtml);
        while (matcher.find()) {
            String html = matcher.group();
            boolean data = html.toLowerCase().contains("<td");
            rows.add(new Row(matcher.start(), matcher.end(), data, data ? dateOf(html) : null,
                    data ? eventOf(html) : -1));
        }
        return rows;
    }

    private static LocalDateTime dateOf(String rowHtml) {
        Matcher matcher = CELL.matcher(rowHtml);
        while (matcher.find()) {
            LocalDateTime parsed = parseDate(text(matcher.group(1)));
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private static int eventOf(String rowHtml) {
        Matcher matcher = CELL.matcher(rowHtml);
        while (matcher.find()) {
            String value = text(matcher.group(1));
            if (EVENT_CELL.matcher(value).matches()) {
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException ignored) {
                    return -1;
                }
            }
        }
        return -1;
    }

    private static LocalDateTime parseDate(String value) {
        Matcher matcher = DATE_CELL.matcher(value);
        if (!matcher.matches()) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(matcher.group(1), DATE);
            String time = matcher.group(2);
            if (time == null) {
                return date.atStartOfDay();
            }
            DateTimeFormatter formatter = time.length() == 5 ? TIME_SHORT : TIME_LONG;
            return date.atTime(LocalTime.parse(time, formatter));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Locates the first table element, including any nested tables, so a cell
     * that itself contains a table is not cut at the inner closing tag.
     *
     * @param html fragment that may contain a movements table
     * @return start and end offsets of the outermost table, or {@code null}
     */
    private static int[] outermostTable(String html) {
        Matcher matcher = TABLE_TAG.matcher(html);
        int start = -1;
        int depth = 0;
        while (matcher.find()) {
            boolean closing = matcher.group().regionMatches(true, 1, "/", 0, 1);
            if (!closing) {
                if (depth == 0) {
                    start = matcher.start();
                }
                depth++;
            } else if (depth > 0) {
                depth--;
                if (depth == 0 && start >= 0) {
                    return new int[] { start, matcher.end() };
                }
            }
        }
        return null;
    }

    private static String text(String html) {
        return decode(TAG.matcher(html).replaceAll(" ")).replaceAll("\\s+", " ").trim();
    }

    private static String decode(String value) {
        return value
                .replace("&amp;", "&")
                .replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    private record Row(int start, int end, boolean data, LocalDateTime when, int eventNumber) {
    }
}
