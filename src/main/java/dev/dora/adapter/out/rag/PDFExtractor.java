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

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Extracts plain text from PDF files using Apache PDFBox for use in the RAG ingestion pipeline.
 *
 * @author Rodrigo Prestes Machado
 */
@ApplicationScoped
public class PDFExtractor {

    /**
     * Extracts text from a PDF file located at the given path.
     *
     * @param path the path to the PDF file
     * @return the extracted text as a String
     */
    public String extractText(Path path) {
        String text = "";
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            if (!document.isEncrypted()) {
                PDFTextStripper stripper = new PDFTextStripper();
                text = stripper.getText(document);
            } else {
                Log.error("The document is encrypted.");
            }
        } catch (IOException e) {
            Log.error("Could not read the file.");
        }
        return text;
    }

    /**
     * Checks if the given file path points to a PDF file.
     *
     * @param filePath the path to the file
     * @return true if the file is a PDF, false otherwise
     */
    public boolean isPdfFile(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase();
        return fileName.endsWith(".pdf");
    }

}
