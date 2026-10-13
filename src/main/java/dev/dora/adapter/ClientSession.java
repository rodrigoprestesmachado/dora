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

import io.quarkus.logging.Log;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/**
 * The conversation that is open for a phone number, either the web chat or WhatsApp.
 *
 * <p>Notices are delivered through whichever interface opened the conversation.
 *
 * @author Rodrigo Prestes Machado
 */
public final class ClientSession {

    private static final ConcurrentMap<String, Consumer<String>> OPEN = new ConcurrentHashMap<>();

    private ClientSession() {
    }

    /**
     * Binds {@code phone} to the interface that should receive notices.
     *
     * @param phone   the client's phone number
     * @param deliver sends one sentence through that interface
     * @return a handle that unbinds this delivery when the conversation turn ends
     */
    public static Registration open(String phone, Consumer<String> deliver) {
        if (phone == null || phone.isBlank() || deliver == null) {
            return () -> {
            };
        }
        OPEN.put(phone, deliver);
        return () -> OPEN.remove(phone, deliver);
    }

    /**
     * Delivers {@code text} through the interface bound to {@code phone}.
     *
     * @param phone the client's phone number
     * @param text  sentence in Portuguese
     */
    public static void deliver(String phone, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        Consumer<String> deliver = phone == null ? null : OPEN.get(phone);
        if (deliver == null) {
            Log.info("Aviso sem conversa aberta: " + text);
            return;
        }
        try {
            deliver.accept(text);
        } catch (RuntimeException error) {
            Log.warn("Não foi possível entregar o aviso da consulta", error);
        }
    }

    /**
     * Unbinds the delivery registered by {@link #open}.
     */
    @FunctionalInterface
    public interface Registration extends AutoCloseable {

        @Override
        void close();
    }
}
