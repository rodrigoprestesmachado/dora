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
 * Domain model that represents a message sent by a user.
 *
 * @author Rodrigo Prestes Machado
 */
public class UserMessage extends Message {

    /**
     * User who sent the message.
     */
    private User user;

    /**
     * Returns the message author.
     *
     * @return the user who sent the message
     */
    public User getUser() {
        return user;
    }

    /**
     * Sets the message author.
     *
     * @param user the user who sent the message
     */
    public void setUser(User user) {
        this.user = user;
    }

}
