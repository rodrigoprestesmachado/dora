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

import java.util.Date;

/**
 * Base domain model that represents a message exchanged within a chat.
 *
 * @author Rodrigo Prestes Machado
 */
public abstract class Message {

    /**
     * Text content of the message.
     */
    private String message;

    /**
     * Chat session that contains this message.
     */
    private Chat chat;

    /**
     * Instant when the message was created.
     */
    private Date timestamp;

    /**
     * Returns the message text.
     *
     * @return the message content
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the message text.
     *
     * @param message the message content to set
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Returns the chat that contains this message.
     *
     * @return the parent chat
     */
    public Chat getChat() {
        return chat;
    }

    /**
     * Sets the chat that contains this message.
     *
     * @param chat the parent chat to set
     */
    public void setChat(Chat chat) {
        this.chat = chat;
    }

    /**
     * Returns the message timestamp.
     *
     * @return the creation timestamp
     */
    public Date getTimestamp() {
        return timestamp;
    }

    /**
     * Sets the message timestamp.
     *
     * @param timestamp the creation timestamp to set
     */
    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

}
