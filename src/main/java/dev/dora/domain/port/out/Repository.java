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

import dev.dora.domain.model.Chat;
import java.util.Optional;

/**
 * Driven port for persisting and loading chat sessions.
 *
 * @author Rodrigo Prestes Machado
 */
public interface Repository {

    /**
     * Finds the most recent chat for the user identified by phone number.
     *
     * @param phoneNumber the user phone number
     * @return the last chat, or empty when the user has no chat yet
     */
    Optional<Chat> findLastByPhone(String phoneNumber);

    /**
     * Saves the given chat as the last active chat for its owner.
     *
     * @param chat the chat to persist
     */
    void save(Chat chat);

}
