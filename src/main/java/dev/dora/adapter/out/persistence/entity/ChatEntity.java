/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */

package dev.dora.adapter.out.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity that persists a chat session.
 *
 * @author Rodrigo Prestes Machado
 */
@Entity
@Table(name = "chat")
public class ChatEntity {

    /**
     * Unique identifier of this chat.
     */
    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    /**
     * Phone number that identifies the chat owner.
     */
    @Column(name = "phone_number", nullable = false, length = 32)
    private String phoneNumber;

    /**
     * Instant when this chat was started.
     */
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    /**
     * Messages that belong to this chat, in chronological order.
     */
    @OneToMany(mappedBy = "chat", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("sequence Asc")
    private List<MessageEntity> messages = new ArrayList<>();

    /**
     * Returns the chat identifier.
     *
     * @return the chat id
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the chat identifier.
     *
     * @param id the chat id to set
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Returns the owner phone number.
     *
     * @return the phone number
     */
    public String getPhoneNumber() {
        return phoneNumber;
    }

    /**
     * Sets the owner phone number.
     *
     * @param phoneNumber the phone number to set
     */
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    /**
     * Returns the chat start timestamp.
     *
     * @return the start timestamp
     */
    public Instant getStartedAt() {
        return startedAt;
    }

    /**
     * Sets the chat start timestamp.
     *
     * @param startedAt the start timestamp to set
     */
    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    /**
     * Returns the persisted messages.
     *
     * @return the message entities
     */
    public List<MessageEntity> getMessages() {
        return messages;
    }

    /**
     * Sets the persisted messages.
     *
     * @param messages the message entities to set
     */
    public void setMessages(List<MessageEntity> messages) {
        this.messages = messages;
    }

}
