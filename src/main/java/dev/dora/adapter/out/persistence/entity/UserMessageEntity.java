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

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * JPA entity that persists a user message inside a chat.
 *
 * <p>The message author's phone number is not duplicated here; it is
 * inherited from the owning {@link ChatEntity#getPhoneNumber()}.</p>
 *
 * @author Rodrigo Prestes Machado
 */
@Entity
@DiscriminatorValue("USER")
public class UserMessageEntity extends MessageEntity {

}
