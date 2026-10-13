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

/**
 * Phone number of the client whose chat is executing a tool on this thread.
 *
 * <p>LangChain4j runs a tool on the same thread that is generating the reply,
 * so a notice sent from that tool can reach the person who is waiting.
 *
 * @author Rodrigo Prestes Machado
 */
public final class ConversationPhone {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private ConversationPhone() {
    }

    /**
     * Sets the phone number bound to the current thread.
     *
     * @param phoneNumber the client's phone, or {@code null} to clear it
     */
    public static void set(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            CURRENT.remove();
        } else {
            CURRENT.set(phoneNumber);
        }
    }

    /**
     * Reads the phone number bound to the current thread.
     *
     * @return the phone set for this thread, or {@code null}
     */
    public static String current() {
        return CURRENT.get();
    }

    /**
     * Drops the phone set for this thread.
     */
    public static void clear() {
        CURRENT.remove();
    }
}
