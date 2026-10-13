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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Domain model that represents a chat session for a user.
 *
 * <p>A new chat is opened when the idle time between consecutive user
 * messages exceeds the caller-provided inactivity threshold (see
 * {@code chat.inactivity-threshold-minutes}, configured by
 * {@code dev.dora.adapter.config.ApplicationBeans} and passed down through
 * {@code dev.dora.application.ChatService}). The domain model intentionally
 * has no default value of its own, so this threshold is not hardcoded here.</p>
 *
 * @author Rodrigo Prestes Machado
 */
public class Chat {

    /**
     * Unique identifier of this chat.
     */
    private String id;

    /**
     * User that owns this chat.
     */
    private User user;

    /**
     * Messages that belong to this chat, in chronological order.
     */
    private List<Message> messages;

    /**
     * Instant when this chat was started.
     */
    private Date startedAt;

    /**
     * Starts a new empty chat for the given user.
     *
     * @param user the user that owns the chat
     * @return a new chat with a generated id and current start time
     */
    public static Chat start(User user) {
        Chat chat = new Chat();
        chat.setId(UUID.randomUUID().toString());
        chat.setUser(user);
        chat.setMessages(new ArrayList<>());
        chat.setStartedAt(new Date());
        return chat;
    }

    /**
     * Assigns an incoming user message to the current chat or opens a new one
     * when the given inactivity threshold is exceeded.
     *
     * @param lastChat the previous chat for the user, or {@code null} if none
     * @param message the incoming user message
     * @param inactivityThresholdMs maximum idle time, in milliseconds, before a new chat starts
     * @return the chat that contains the message after acceptance
     */
    public static Chat accept(Chat lastChat, UserMessage message, long inactivityThresholdMs) {
        if (lastChat == null || lastChat.isExpiredAt(message.getTimestamp(),
                inactivityThresholdMs)) {
            Chat chat = start(message.getUser());
            chat.addMessage(message);
            return chat;
        }
        lastChat.addMessage(message);
        return lastChat;
    }

    /**
     * Returns whether this chat is expired at the given instant.
     *
     * @param instant the instant to evaluate against the last activity
     * @param inactivityThresholdMs maximum idle time, in milliseconds, before the chat is
     *     considered expired
     * @return {@code true} when the idle time is greater than the given threshold
     */
    public boolean isExpiredAt(Date instant, long inactivityThresholdMs) {
        if (instant == null) {
            return false;
        }
        Date lastActivity = lastMessageAt();
        return instant.getTime() - lastActivity.getTime() > inactivityThresholdMs;
    }

    /**
     * Returns the timestamp of the last user message, or the chat start time
     * when the chat still has no user messages.
     *
     * <p>Inactivity is measured against user activity only, so an automatic
     * agent reply does not by itself keep a chat session alive.</p>
     *
     * @return the last user activity timestamp
     */
    public Date lastMessageAt() {
        List<UserMessage> userMessages = getUserMessages();
        if (userMessages.isEmpty()) {
            return startedAt;
        }
        return userMessages.get(userMessages.size() - 1).getTimestamp();
    }

    /**
     * Adds a message to this chat and links the message back to it.
     *
     * @param message the message to add
     */
    public void addMessage(Message message) {
        if (messages == null) {
            messages = new ArrayList<>();
        }
        message.setChat(this);
        messages.add(message);
    }

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
     * Returns the user that owns this chat.
     *
     * @return the chat owner
     */
    public User getUser() {
        return user;
    }

    /**
     * Sets the user that owns this chat.
     *
     * @param user the chat owner to set
     */
    public void setUser(User user) {
        this.user = user;
    }

    /**
     * Returns an unmodifiable view of all messages in this chat, in
     * chronological order.
     *
     * @return the chat messages
     */
    public List<Message> getMessages() {
        if (messages == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(messages);
    }

    /**
     * Sets the messages that belong to this chat.
     *
     * @param messages the messages to set
     */
    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    /**
     * Returns an unmodifiable view of the user messages in this chat, in
     * chronological order.
     *
     * @return the user messages
     */
    public List<UserMessage> getUserMessages() {
        return getMessages().stream()
                .filter(UserMessage.class::isInstance)
                .map(UserMessage.class::cast)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Returns an unmodifiable view of the agent replies in this chat, in
     * chronological order.
     *
     * @return the agent messages
     */
    public List<AgentMessage> getAgentMessages() {
        return getMessages().stream()
                .filter(AgentMessage.class::isInstance)
                .map(AgentMessage.class::cast)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Returns the instant when this chat was started.
     *
     * @return the start timestamp
     */
    public Date getStartedAt() {
        return startedAt;
    }

    /**
     * Sets the instant when this chat was started.
     *
     * @param startedAt the start timestamp to set
     */
    public void setStartedAt(Date startedAt) {
        this.startedAt = startedAt;
    }

}
