/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */

package dev.dora.adapter.out.ai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Model-facing tool texts loaded from {@code prompts/dora-tools.txt}.
 *
 * @author Rodrigo Prestes Machado
 */
final class DoraToolTexts {

    static final String RESOURCE = "prompts/dora-tools.txt";

    private final Map<String, String> entries;

    private DoraToolTexts(Map<String, String> entries) {
        this.entries = entries;
    }

    /**
     * Loads the tool texts from the classpath.
     *
     * @return the parsed texts
     */
    static DoraToolTexts load() {
        try (InputStream input = DoraToolTexts.class.getClassLoader()
                .getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Recurso ausente: " + RESOURCE);
            }
            String source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return new DoraToolTexts(parse(source));
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível ler " + RESOURCE, exception);
        }
    }

    /**
     * Returns the text for a tool or a parameter.
     *
     * @param key tool name, or {@code tool.parameter}
     * @return the text, never blank
     */
    String text(String key) {
        String value = entries.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Texto ausente em " + RESOURCE + ": " + key);
        }
        return value;
    }

    /**
     * Keys present in the file, in declaration order.
     *
     * @return the keys
     */
    Set<String> keys() {
        return entries.keySet();
    }

    /**
     * Parses the tool text file. Wrapped lines of a block are joined with a space.
     *
     * @param source file contents
     * @return keys mapped to their text
     */
    static Map<String, String> parse(String source) {
        Map<String, String> entries = new LinkedHashMap<>();
        String key = null;
        List<String> lines = new ArrayList<>();
        for (String raw : source.split("\\R", -1)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (isHeader(line)) {
                store(entries, key, lines);
                key = line.substring(1, line.length() - 1);
                lines = new ArrayList<>();
                continue;
            }
            if (key == null) {
                throw new IllegalStateException(
                        "Linha fora de um bloco em " + RESOURCE + ": " + line);
            }
            lines.add(line);
        }
        store(entries, key, lines);
        return Map.copyOf(entries);
    }

    private static boolean isHeader(String line) {
        return line.startsWith("[") && line.endsWith("]") && line.length() > 2
                && !line.substring(1, line.length() - 1).contains("[");
    }

    private static void store(Map<String, String> entries, String key, List<String> lines) {
        if (key == null) {
            return;
        }
        if (lines.isEmpty()) {
            throw new IllegalStateException("Bloco vazio em " + RESOURCE + ": " + key);
        }
        if (entries.containsKey(key)) {
            throw new IllegalStateException("Bloco repetido em " + RESOURCE + ": " + key);
        }
        entries.put(key, String.join(" ", lines));
    }
}
