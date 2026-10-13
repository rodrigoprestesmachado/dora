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

import java.util.Optional;

/**
 * Driven port (out) for transcribing audio content to text.
 *
 * @author Rodrigo Prestes Machado
 */
public interface SpeechToTextPort {

    /**
     * Transcribes audio data to text.
     *
     * @param audioData raw bytes of the audio file
     * @param mimeType  MIME type of the audio (e.g. {@code audio/ogg})
     * @return an {@link Optional} with the transcribed text, or empty if transcription failed
     */
    Optional<String> transcribe(byte[] audioData, String mimeType);
}
