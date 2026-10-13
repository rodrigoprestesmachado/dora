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

/**
 * Driven port for a short progress message sent to the client who is waiting,
 * outside the assistant's final reply. The open conversation decides whether
 * that message is shown on the web chat or sent on WhatsApp.
 *
 * @author Rodrigo Prestes Machado
 */
public interface ClientNoticePort {

    /**
     * Sends a progress message to the client of the consultation in progress.
     *
     * @param text message in Portuguese
     */
    void notify(String text);
}
