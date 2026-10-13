/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */

package dev.dora.adapter.out.persistence;

import dev.dora.adapter.out.persistence.entity.AgentMessageEntity;
import dev.dora.adapter.out.persistence.entity.ChatEntity;
import dev.dora.adapter.out.persistence.entity.MessageEntity;
import dev.dora.adapter.out.persistence.entity.UserMessageEntity;
import dev.dora.domain.model.AgentMessage;
import dev.dora.domain.model.Chat;
import dev.dora.domain.model.Message;
import dev.dora.domain.model.User;
import dev.dora.domain.model.UserMessage;
import dev.dora.domain.port.out.Repository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL adapter that persists chat sessions with Hibernate Panache.
 *
 * @author Rodrigo Prestes Machado
 */
@ApplicationScoped
public class ChatRepository implements Repository, PanacheRepositoryBase<ChatEntity, String> {

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public Optional<Chat> findLastByPhone(String phoneNumber) {
        return find("phoneNumber = ?1 order by startedAt desc", phoneNumber)
                .firstResultOptional()
                .map(this::toDomain);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void save(Chat chat) {
        if (chat == null || chat.getUser() == null || chat.getUser().getPhoneNumber() == null) {
            throw new IllegalArgumentException("Chat must have a user with a phone number");
        }

        ChatEntity entity = findById(chat.getId());
        boolean isNew = entity == null;
        if (isNew) {
            entity = new ChatEntity();
            entity.setId(chat.getId());
        }

        entity.setPhoneNumber(chat.getUser().getPhoneNumber());
        entity.setStartedAt(toInstant(chat.getStartedAt()));
        entity.getMessages().clear();

        if (isNew) {
            persist(entity);
        }

        List<Message> messages = chat.getMessages();
        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            MessageEntity messageEntity = toEntity(message);
            messageEntity.setChat(entity);
            messageEntity.setMessage(message.getMessage());
            messageEntity.setCreatedAt(toInstant(message.getTimestamp()));
            messageEntity.setSequence(i);
            entity.getMessages().add(messageEntity);
        }
    }

    /**
     * Creates the persistence entity that matches the domain message type.
     *
     * @param message the domain message to convert
     * @return a new, still unpopulated-with-common-fields message entity
     */
    private MessageEntity toEntity(Message message) {
        if (message instanceof UserMessage) {
            return new UserMessageEntity();
        }
        return new AgentMessageEntity();
    }

    /**
     * Maps a persistence entity to the domain chat aggregate.
     *
     * @param entity the chat entity
     * @return the domain chat
     */
    private Chat toDomain(ChatEntity entity) {
        User user = new User();
        user.setPhoneNumber(entity.getPhoneNumber());

        Chat chat = new Chat();
        chat.setId(entity.getId());
        chat.setUser(user);
        chat.setStartedAt(toDate(entity.getStartedAt()));

        List<Message> messages = new ArrayList<>();
        for (MessageEntity messageEntity : entity.getMessages()) {
            Message message = toDomainMessage(messageEntity, user);
            message.setMessage(messageEntity.getMessage());
            message.setTimestamp(toDate(messageEntity.getCreatedAt()));
            message.setChat(chat);
            messages.add(message);
        }
        chat.setMessages(messages);
        return chat;
    }

    /**
     * Creates the domain message that matches the persistence entity type.
     *
     * @param messageEntity the persisted message entity
     * @param user the chat owner, used for user messages
     * @return a new, still unpopulated-with-common-fields domain message
     */
    private Message toDomainMessage(MessageEntity messageEntity, User user) {
        if (messageEntity instanceof UserMessageEntity) {
            UserMessage userMessage = new UserMessage();
            userMessage.setUser(user);
            return userMessage;
        }
        return new AgentMessage();
    }

    /**
     * Converts a domain {@link Date} to an {@link Instant}.
     *
     * @param date the date to convert
     * @return the instant, or {@code null} when the date is null
     */
    private static Instant toInstant(Date date) {
        return date == null ? null : date.toInstant();
    }

    /**
     * Converts an {@link Instant} to a domain {@link Date}.
     *
     * @param instant the instant to convert
     * @return the date, or {@code null} when the instant is null
     */
    private static Date toDate(Instant instant) {
        return instant == null ? null : Date.from(instant);
    }

}
