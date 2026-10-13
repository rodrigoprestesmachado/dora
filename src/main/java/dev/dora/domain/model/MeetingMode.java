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
 * Whether a meeting happens on a video call or at the office.
 *
 * @author Rodrigo Prestes Machado
 */
public enum MeetingMode {

    /** Google Meet, with an invitation sent to the client's e-mail. */
    ONLINE,

    /** At the office. The invitation carries the address and goes to the client's e-mail. */
    IN_PERSON
}
