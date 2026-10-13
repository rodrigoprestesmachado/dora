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

import dev.dora.domain.port.out.ClientNoticePort;
import io.quarkus.logging.Log;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sends a short update every few seconds while a consultation is still running.
 *
 * <p>The first update waits one interval, so a consultation that finishes quickly stays quiet.
 * Closing this object stops further updates.
 *
 * @author Rodrigo Prestes Machado
 */
public final class ClientProgress implements AutoCloseable {

    /** Interval between updates while the TJRS consultation is running. */
    public static final long INTERVAL_SECONDS = 10;

    private static final List<String> UPDATES = List.of(
            "Ainda estou consultando o TJRS. Já te respondo.",
            "A consulta no site do tribunal continua.",
            "O TJRS está demorando um pouco. Sigo aguardando o resultado.");

    private static final ClientProgress IDLE = new ClientProgress(null, null,
            new AtomicBoolean(true));

    private final ScheduledExecutorService executor;
    private final ScheduledFuture<?> future;
    private final AtomicBoolean closed;

    private ClientProgress(ScheduledExecutorService executor, ScheduledFuture<?> future,
            AtomicBoolean closed) {
        this.executor = executor;
        this.future = future;
        this.closed = closed;
    }

    /**
     * Starts updates every {@link #INTERVAL_SECONDS} seconds for the given phone.
     *
     * @param phone   phone number of the person who is waiting, or blank to stay quiet
     * @param notices port that delivers each update
     * @return a progress handle; close it when the consultation finishes
     */
    public static ClientProgress everyTenSeconds(String phone, ClientNoticePort notices) {
        return start(phone, notices, INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Starts updates on the given cadence. Tests use a short interval.
     *
     * @param phone    phone number, or blank to stay quiet
     * @param notices  port that delivers each update
     * @param interval delay before the first update and between updates
     * @param unit     unit of {@code interval}
     * @return a progress handle; close it when the consultation finishes
     */
    public static ClientProgress start(String phone, ClientNoticePort notices, long interval,
            TimeUnit unit) {
        if (phone == null || phone.isBlank() || notices == null || interval <= 0) {
            return IDLE;
        }
        AtomicBoolean closed = new AtomicBoolean();
        AtomicInteger sent = new AtomicInteger();
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "dora-client-progress");
            thread.setDaemon(true);
            return thread;
        });
        ScheduledFuture<?> future = executor.scheduleWithFixedDelay(() -> send(phone, notices,
                closed, sent),
                interval, interval, unit);
        return new ClientProgress(executor, future, closed);
    }

    private static void send(String phone, ClientNoticePort notices, AtomicBoolean closed,
            AtomicInteger sent) {
        if (closed.get()) {
            return;
        }
        String text = UPDATES.get(sent.getAndIncrement() % UPDATES.size());
        ConversationPhone.set(phone);
        try {
            notices.notify(text);
        } catch (RuntimeException error) {
            Log.warn("Não foi possível avisar o cliente sobre o andamento da consulta no TJRS",
                    error);
        } finally {
            ConversationPhone.clear();
        }
    }

    /**
     * Stops further updates. An update already being sent is left to finish.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        future.cancel(false);
        executor.shutdownNow();
    }
}
