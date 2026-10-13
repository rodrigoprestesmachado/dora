/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */

package dev.dora.adapter.in.rest;

import dev.dora.adapter.ClientSession;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;

/**
 * Inserts progress sentences into the web chat response already open for a phone.
 * The page reads each sentence between {@link #MARK} characters.
 *
 * @author Rodrigo Prestes Machado
 */
public final class WebChatProgress {

    /** Delimits one progress sentence inside the plain-text chat stream. */
    static final char MARK = '\u001e';

    private WebChatProgress() {
    }

    /**
     * Merges progress sentences for {@code phone} into {@code answer} and binds that
     * phone to the web interface until the answer completes.
     *
     * @param phone  phone number of the web chat
     * @param answer the assistant reply
     * @return the reply plus any progress sentences emitted while it is running
     */
    public static Multi<String> interleave(String phone, Multi<String> answer) {
        Slot slot = new Slot();
        Multi<String> progress = Multi.createFrom().emitter(emitter -> {
            synchronized (slot.lock) {
                slot.emitter = emitter;
            }
            ClientSession.Registration registration = ClientSession.open(phone, text -> emit(slot,
                    text));
            emitter.onTermination(registration::close);
        });
        Multi<String> reply = answer.onTermination().invoke(() -> finish(slot));
        return Multi.createBy().merging().streams(progress, reply);
    }

    private static void emit(Slot slot, String text) {
        synchronized (slot.lock) {
            if (slot.emitter == null || slot.emitter.isCancelled()) {
                return;
            }
            slot.emitter.emit(MARK + text.replace(MARK, ' ') + MARK);
        }
    }

    private static void finish(Slot slot) {
        synchronized (slot.lock) {
            MultiEmitter<? super String> emitter = slot.emitter;
            slot.emitter = null;
            if (emitter != null && !emitter.isCancelled()) {
                emitter.complete();
            }
        }
    }

    private static final class Slot {
        private final Object lock = new Object();
        private MultiEmitter<? super String> emitter;
    }
}
