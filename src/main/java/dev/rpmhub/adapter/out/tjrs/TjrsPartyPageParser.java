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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the TJRS "por nome da parte" HTML: the table of matching people and the
 * table of a person's active processes.
 *
 * <p>The legado name list repeats eproc rows without a CPF. Rows that share a
 * name are collapsed to one person per CPF, preferring the eproc link because
 * that page also loads legado processes.
 *
 * @author Rodrigo Prestes Machado
 */
class TjrsPartyPageParser {

    private static final Pattern ROW = Pattern.compile("(?is)<tr\\b[^>]*>(.*?)</tr>");
    private static final Pattern CELL = Pattern.compile("(?is)<t[dh]\\b[^>]*>(.*?)</t[dh]>");
    private static final Pattern ANCHOR = Pattern.compile("(?is)<a\\b[^>]*href\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>(.*?)</a>");
    private static final Pattern TAG = Pattern.compile("(?is)<[^>]+>");

    private static final String PEOPLE_HREF = "processos-por-nome";
    private static final String PROCESS_HREF = "processo/resumo";

    /**
     * A person row on the name-results page.
     *
     * @param name      display name
     * @param cpfDigits CPF digits, or an empty string when the court omitted it
     * @param cpfLabel  CPF/CNPJ text shown in the table
     * @param href      link that opens this person's process list
     * @param fromEproc {@code true} when the link targets the eproc party
     */
    record PersonRow(String name, String cpfDigits, String cpfLabel, String href, boolean fromEproc) {
    }

    /**
     * A process row on the process-list page.
     *
     * @param number       CNJ or Themis number, as shown
     * @param party        party name shown in the row
     * @param comarca      court district
     * @param className    CNJ class
     * @param lastMovement date or description of the latest movement
     */
    record ProcessRow(String number, String party, String comarca, String className, String lastMovement) {
    }

    /**
     * Extracts person rows whose link opens {@code /partes/processos-por-nome}.
     *
     * @param html result HTML, typically the {@code .content} fragment
     * @return person rows in document order; empty when the page has none
     */
    List<PersonRow> parsePeople(String html) {
        List<PersonRow> people = new ArrayList<>();
        if (html == null || html.isBlank()) {
            return people;
        }
        Matcher rows = ROW.matcher(html);
        while (rows.find()) {
            String row = rows.group(1);
            Matcher anchors = ANCHOR.matcher(row);
            String href = null;
            String name = null;
            while (anchors.find()) {
                if (anchors.group(1).contains(PEOPLE_HREF)) {
                    href = decode(anchors.group(1));
                    name = text(anchors.group(2));
                    break;
                }
            }
            if (href == null || name.isBlank()) {
                continue;
            }
            List<String> cells = cellTexts(row);
            String cpfLabel = cells.size() >= 2 ? cells.get(1) : "";
            if (cpfLabel.isBlank()) {
                cpfLabel = "CPF/CNPJ não informado";
            }
            // The public page masks the document ("039.9**.***-**"). The visible
            // digits still distinguish two records that share the exact name.
            String cpfDigits = digitsBeforeMask(cpfLabel);
            boolean fromEproc = href.toLowerCase(Locale.ROOT).contains("from=eproc")
                    || href.toLowerCase(Locale.ROOT).contains("parteselecionadafrom=eproc");
            people.add(new PersonRow(name, cpfDigits, cpfLabel, href, fromEproc));
        }
        return people;
    }

    /**
     * Extracts process rows whose link opens {@code /processo/resumo}.
     *
     * @param html result HTML, typically the {@code .content} fragment
     * @return process rows in document order; empty when the page has none
     */
    List<ProcessRow> parseProcesses(String html) {
        List<ProcessRow> processes = new ArrayList<>();
        if (html == null || html.isBlank()) {
            return processes;
        }
        Matcher rows = ROW.matcher(html);
        while (rows.find()) {
            String row = rows.group(1);
            Matcher anchors = ANCHOR.matcher(row);
            String number = null;
            while (anchors.find()) {
                if (anchors.group(1).contains(PROCESS_HREF)) {
                    number = text(anchors.group(2));
                    break;
                }
            }
            if (number == null || number.isBlank()) {
                continue;
            }
            List<String> cells = cellTexts(row);
            processes.add(new ProcessRow(
                    number,
                    cell(cells, 1),
                    cell(cells, 2),
                    cell(cells, 3),
                    cells.size() >= 6 ? cell(cells, 5) : cell(cells, 4)));
        }
        return processes;
    }

    /**
     * Collapses duplicate legado/eproc rows into one person per CPF.
     *
     * <p>A legado row without a CPF is dropped when the same name already has a
     * CPF. Names that only appear without a CPF stay as a single person.
     *
     * @param rows person rows, possibly repeated across the eproc and legado tables
     * @return distinct people, eproc link preferred when both exist
     */
    List<PersonRow> distinctPeople(List<PersonRow> rows) {
        Map<String, List<PersonRow>> byName = new LinkedHashMap<>();
        for (PersonRow row : rows) {
            if (row.name() == null || row.name().isBlank()) {
                continue;
            }
            byName.computeIfAbsent(row.name().toUpperCase(Locale.ROOT), key -> new ArrayList<>()).add(row);
        }

        List<PersonRow> people = new ArrayList<>();
        for (List<PersonRow> group : byName.values()) {
            Map<String, PersonRow> byCpf = new LinkedHashMap<>();
            PersonRow withoutCpf = null;
            for (PersonRow row : group) {
                if (row.cpfDigits().length() < 4) {
                    withoutCpf = prefer(withoutCpf, row);
                } else {
                    byCpf.merge(row.cpfDigits(), row, TjrsPartyPageParser::prefer);
                }
            }
            if (byCpf.isEmpty()) {
                if (withoutCpf != null) {
                    people.add(withoutCpf);
                }
            } else {
                people.addAll(byCpf.values());
            }
        }
        return people;
    }

    /**
     * Keeps only rows whose name equals the name the client typed.
     *
     * @param people         distinct people from the court page
     * @param normalizedName upper-case name with collapsed whitespace
     * @return people with that exact name
     */
    List<PersonRow> exactNames(List<PersonRow> people, String normalizedName) {
        List<PersonRow> matches = new ArrayList<>();
        if (normalizedName == null || normalizedName.isBlank()) {
            return matches;
        }
        String expected = normalizedName.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        for (PersonRow person : people) {
            String name = person.name().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
            if (expected.equals(name)) {
                matches.add(person);
            }
        }
        return matches;
    }

    /**
     * Digits shown before TJRS masks the rest of the document with {@code *}.
     *
     * @param label CPF/CNPJ cell text
     * @return the visible digits, or an empty string when none are shown
     */
    static String digitsBeforeMask(String label) {
        if (label == null || label.isBlank()) {
            return "";
        }
        int star = label.indexOf('*');
        String visible = star >= 0 ? label.substring(0, star) : label;
        return visible.replaceAll("\\D", "");
    }

    /**
     * Renders a Markdown table of people, truncated to {@code limit} rows.
     *
     * @param people distinct people
     * @param limit  maximum rows to include
     * @return the table, with a note when the list was truncated
     */
    String peopleMarkdown(List<PersonRow> people, int limit) {
        int capped = Math.max(1, limit);
        StringBuilder markdown = new StringBuilder();
        markdown.append("| Nome |\n| --- |\n");
        int shown = Math.min(capped, people.size());
        for (int i = 0; i < shown; i++) {
            PersonRow person = people.get(i);
            markdown.append("| ").append(cell(person.name())).append(" |\n");
        }
        if (people.size() > capped) {
            markdown.append("\nHá mais pessoas além destas ").append(capped)
                    .append(". Peça um nome mais específico.\n");
        }
        return markdown.toString().trim();
    }

    /**
     * Renders a Markdown table of processes, truncated to {@code limit} rows.
     *
     * @param processes process rows
     * @param limit     maximum rows to include
     * @return the table, with a note when the list was truncated
     */
    String processesMarkdown(List<ProcessRow> processes, int limit) {
        int capped = Math.max(1, limit);
        StringBuilder markdown = new StringBuilder();
        markdown.append("| Número | Parte | Comarca | Classe | Última movimentação |\n| --- | --- | --- | --- | --- |\n");
        int shown = Math.min(capped, processes.size());
        for (int i = 0; i < shown; i++) {
            ProcessRow process = processes.get(i);
            markdown.append("| ").append(cell(process.number()))
                    .append(" | ").append(cell(process.party()))
                    .append(" | ").append(cell(process.comarca()))
                    .append(" | ").append(cell(process.className()))
                    .append(" | ").append(cell(process.lastMovement()))
                    .append(" |\n");
        }
        if (processes.size() > capped) {
            markdown.append("\nHá mais processos além destes ").append(capped)
                    .append(". A lista mostra os primeiros resultados de processos ativos.\n");
        }
        return markdown.toString().trim();
    }

    private static PersonRow prefer(PersonRow current, PersonRow candidate) {
        if (current == null) {
            return candidate;
        }
        if (candidate.fromEproc() && !current.fromEproc()) {
            return candidate;
        }
        return current;
    }

    private static List<String> cellTexts(String rowHtml) {
        List<String> cells = new ArrayList<>();
        Matcher matcher = CELL.matcher(rowHtml);
        while (matcher.find()) {
            cells.add(text(matcher.group(1)));
        }
        return cells;
    }

    private static String cell(List<String> cells, int index) {
        if (index >= cells.size()) {
            return "";
        }
        return cells.get(index);
    }

    private static String cell(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replace("|", "\\|").replace("\n", " ").trim();
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
}
