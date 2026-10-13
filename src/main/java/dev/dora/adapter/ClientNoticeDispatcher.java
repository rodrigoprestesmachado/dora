/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */

package dev.dora.adapter;

import dev.dora.domain.port.out.ClientNoticePort;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Delivers a consultation notice through the interface that opened the current
 * conversation: the web chat or WhatsApp.
 *
 * @author Rodrigo Prestes Machado
 */
@ApplicationScoped
public class ClientNoticeDispatcher implements ClientNoticePort {

    /**
     * {@inheritDoc}
     */
    @Override
    public void notify(String text) {
        ClientSession.deliver(ConversationPhone.current(), text);
    }
}
