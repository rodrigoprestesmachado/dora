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
 * Driven port (out) for downloading media attachments (e.g. WhatsApp voice notes).
 *
 * @author Rodrigo Prestes Machado
 */
public interface MediaDownloaderPort {

    /**
     * Downloads the binary content of a media item by its provider-assigned identifier.
     *
     * @param mediaId media identifier (e.g. the {@code audio.id} field from a webhook payload)
     * @return an {@link Optional} with the downloaded bytes, or empty if the download failed
     */
    Optional<byte[]> downloadMedia(String mediaId);
}
