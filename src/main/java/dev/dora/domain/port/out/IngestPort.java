/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */

package dev.dora.domain.port.out;

import dev.dora.domain.model.DocumentData;
import java.util.List;

/**
 * Driven port (out) for document ingestion into the embedding store.
 *
 * @author Rodrigo Prestes Machado
 */
public interface IngestPort {

    /**
     * Ingests documents from the specified directory.
     *
     * @param directoryPath the path to the directory containing the documents
     */
    void ingestDocuments(String directoryPath);

    /**
     * Ingests the given documents into the embedding store (chunking + embedding + persist).
     *
     * @param documents the list of documents to ingest
     */
    void ingestDocuments(List<DocumentData> documents);
}
