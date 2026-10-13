/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */

package dev.dora.domain.model;

/**
 * Domain representation of a document to be ingested into the embedding store.
 * Replaces framework-specific types (e.g. LangChain4j Document) in domain ports.
 *
 * @author Rodrigo Prestes Machado
 */
public class DocumentData {

    /** Full textual content of the document to be embedded. */
    private final String text;
    /** Origin of the document, such as a file path or a URL. */
    private final String source;

    /**
     * Creates a DocumentData with the given text content and source reference.
     *
     * @param text   full textual content of the document
     * @param source origin of the document (file path, URL, etc.)
     */
    public DocumentData(String text, String source) {
        this.text = text;
        this.source = source;
    }

    public String getText() {
        return text;
    }

    public String getSource() {
        return source;
    }
}
