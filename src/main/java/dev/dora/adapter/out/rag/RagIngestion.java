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

import static dev.langchain4j.data.document.splitter.DocumentSplitters.recursive;

import dev.dora.domain.model.DocumentData;
import dev.dora.domain.port.out.IngestPort;
import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.HuggingFaceTokenCountEstimator;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of the {@link IngestPort} port using LangChain4j, backed by the
 * pgvector-based {@link EmbeddingStore} and a local embedding model.
 *
 * @author Rodrigo Prestes Machado
 */
@ApplicationScoped
public class RagIngestion implements IngestPort {

    /** LangChain4j store that holds vectorised text segments. */
    private final EmbeddingStore<TextSegment> embeddingStore;
    /** Model used to vectorise chunks before persisting them into the store. */
    private final EmbeddingModel embeddingModel;
    /** Service used to extract text from PDF documents before ingestion. */
    private final PDFExtractor pdfService;

    /**
     * Creates a RagIngestion with all required collaborators.
     *
     * @param embeddingStore      LangChain4j store holding vectorised text segments
     * @param embeddingModel      model used to embed chunks before persisting them
     * @param pdfExtractorService service used to extract text from PDFs before ingestion
     */
    @Inject
    public RagIngestion(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel,
            PDFExtractor pdfExtractorService) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.pdfService = pdfExtractorService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void ingestDocuments(String directoryPath) {
        try {
            List<Document> documents = new ArrayList<>();
            Path dirPath = Path.of(directoryPath);

            try (var files = Files.walk(dirPath)) {
                files.filter(Files::isRegularFile)
                        .forEach(file -> {
                            if (pdfService.isPdfFile(file)) {
                                String extractedText = pdfService.extractText(file);
                                if (!extractedText.isEmpty()) {
                                    Document pdfDocument = Document.from(extractedText);
                                    documents.add(pdfDocument);
                                    Log.info("PDF processed: " + file.getFileName());
                                }
                            } else {
                                try {
                                    Document fileDoc = FileSystemDocumentLoader.loadDocument(file);
                                    documents.add(fileDoc);
                                    Log.info("File processed: " + file.getFileName());
                                } catch (BlankDocumentException e) {
                                    Log.warn("Skipping blank file: " + file.getFileName());
                                }
                            }
                        });
            }

            Log.info("📂 Total filesystem documents loaded from directory '"
                    + directoryPath + "': " + documents.size());

            if (documents.isEmpty()) {
                Log.info("📭 No valid documents found to ingest, skipping ingestion.");
                return;
            }

            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .embeddingStore(embeddingStore)
                    .embeddingModel(embeddingModel)
                    .documentSplitter(recursive(800, 300, new HuggingFaceTokenCountEstimator()))
                    .build();

            ingestor.ingest(documents);
            Log.info("Ingestion completed successfully!");

        } catch (IOException e) {
            Log.error("Error processing directory: " + directoryPath, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void ingestDocuments(List<DocumentData> documents) {
        if (documents == null || documents.isEmpty()) {
            Log.info("📭 No documents to ingest.");
            return;
        }
        List<Document> langChainDocs = documents.stream()
                .map(dd -> Document.from(dd.getText(), Metadata.from("source", dd.getSource())))
                .toList();
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .documentSplitter(recursive(800, 300, new HuggingFaceTokenCountEstimator()))
                .build();
        ingestor.ingest(langChainDocs);
        Log.info("✅ Ingestion of " + documents.size() + " document(s) completed successfully!");
    }
}
