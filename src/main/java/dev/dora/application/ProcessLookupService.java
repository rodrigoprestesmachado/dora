/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */

package dev.dora.application;

import dev.dora.domain.model.PartySearchResult;
import dev.dora.domain.model.ProcessLookupResult;
import dev.dora.domain.port.in.ProcessLookupUseCase;
import dev.dora.domain.port.out.ClientNoticePort;
import dev.dora.domain.port.out.ProcessLookupException;
import dev.dora.domain.port.out.ProcessLookupPort;
import dev.dora.domain.validation.CnjProcessNumberValidator;
import dev.dora.domain.validation.PersonNameValidator;
import io.quarkus.logging.Log;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Application service that validates a process number or a person's name,
 * delegates the lookup to a {@link ProcessLookupPort} and translates every
 * outcome (success, invalid input, not found, technical failure) into a clear
 * message.
 *
 * <p>This class is deliberately framework-agnostic (plain Java) so it can be
 * unit tested without a CDI container. Its lifecycle and wiring are handled by
 * {@code dev.dora.adapter.config.ApplicationBeans}.
 *
 * @author Rodrigo Prestes Machado
 */
public class ProcessLookupService implements ProcessLookupUseCase {

    /** Told to the client before the single automatic retry. */
    static final String RETRY_NOTICE = "A consulta no TJRS não concluiu. Vou tentar mais uma vez.";

    /** Port used to perform the actual lookup against the court's search system. */
    private final ProcessLookupPort processLookupPort;

    /** Port used to tell the waiting client that the consultation will be retried. */
    private final ClientNoticePort clientNotice;

    /**
     * Creates a ProcessLookupService with the given lookup port.
     *
     * @param processLookupPort port used to look up a process by its CNJ number or by a person's
     *     name
     * @param clientNotice      port used to warn the client before the automatic retry
     */
    public ProcessLookupService(ProcessLookupPort processLookupPort,
            ClientNoticePort clientNotice) {
        this.processLookupPort = processLookupPort;
        this.clientNotice = clientNotice;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String lookup(String rawProcessNumber) {
        String normalized = CnjProcessNumberValidator.normalize(rawProcessNumber);
        if (normalized == null) {
            return "O número de processo \"" + safe(rawProcessNumber) + "\" não é válido. "
                    + "Peça ao cliente o número completo no formato CNJ, por exemplo "
                    + "5033013-66.2026.8.21.0022 (com ou sem os pontos e traços).";
        }

        return withRetry(() -> readLookup(normalized), "o processo " + normalized,
                "Não foi possível consultar o processo " + normalized
                        + " agora, nem na segunda tentativa. O site do TJRS "
                        + "pode estar indisponível ou ter mudado de layout. "
                        + "O cliente já foi avisado da nova tentativa. "
                        + "Peça para tentar de novo mais tarde.",
                "Ocorreu um erro inesperado ao consultar o processo " + normalized
                        + ", também na segunda tentativa. Tente novamente mais tarde.");
    }

    private String readLookup(String normalized) {
        Optional<ProcessLookupResult> result = processLookupPort.lookup(normalized);
        if (result.isEmpty()) {
            return "Não foi possível localizar o processo " + normalized + " no TJRS. "
                    + "Ele pode não existir, estar em segredo de justiça, ou tramitar em outro "
                    + "tribunal. "
                    + "Confirme o número com o cliente.";
        }
        return result.get().getMarkdown();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String searchByPerson(String rawPersonName) {
        String normalizedName = PersonNameValidator.normalize(rawPersonName);
        if (normalizedName == null) {
            return "O nome \"" + safe(rawPersonName) + "\" não serve para a consulta do TJRS. "
                    + "Peça ao cliente o nome e o sobrenome (pelo menos duas palavras).";
        }

        return withRetry(() -> formatPartySearch(processLookupPort.searchByPerson(normalizedName),
                normalizedName),
                "processos de " + normalizedName,
                "Não foi possível consultar processos de " + normalizedName
                        + " no TJRS, nem na segunda tentativa. O site do TJRS "
                        + "pode estar indisponível ou ter mudado de layout. "
                        + "O cliente já foi avisado da nova tentativa. "
                        + "Peça para tentar de novo mais tarde.",
                "Ocorreu um erro inesperado ao consultar processos de " + normalizedName
                        + " no TJRS, também na segunda tentativa. Tente novamente mais tarde.");
    }

    /**
     * Runs the consultation once. On a technical failure, warns the client and tries
     * exactly one more time. A legitimate "not found" result is returned as-is.
     */
    private String withRetry(Supplier<String> call, String subject, String exhaustedMessage,
            String unexpectedMessage) {
        try {
            return call.get();
        } catch (RuntimeException first) {
            logFailure(first, subject, false);
            clientNotice.notify(RETRY_NOTICE);
            try {
                return call.get();
            } catch (RuntimeException second) {
                logFailure(second, subject, true);
                return second instanceof ProcessLookupException
                        ? exhaustedMessage : unexpectedMessage;
            }
        }
    }

    private static void logFailure(RuntimeException error, String subject, boolean retry) {
        String attempt = retry ? " (nova tentativa)" : "";
        if (error instanceof ProcessLookupException) {
            Log.warnf(error, "⚠️ Falha ao consultar %s no TJRS%s: %s", subject, attempt,
                    failureDetail(error));
        } else {
            Log.errorf(error, "❌ Erro inesperado ao consultar %s no TJRS%s: %s", subject, attempt,
                    failureDetail(error));
        }
    }

    private static String formatPartySearch(PartySearchResult result, String normalizedName) {
        return switch (result.getKind()) {
            case NOT_FOUND -> "Não foi possível localizar \"" + normalizedName
                    + "\" na consulta por nome exato do TJRS, nem entre processos ativos nem "
                    + "entre baixados. "
                    + "Essa consulta não lista processos em segredo de justiça (por exemplo, "
                    + "família e sucessões). "
                    + "Confirme o nome com o cliente. Se ele tiver o número CNJ, consulte o "
                    + "andamento por esse número.";
            case AMBIGUOUS_PEOPLE -> result.getMarkdown()
                    + "\n\nHá mais de um cadastro com o nome exato \"" + normalizedName
                    + "\". Apresente a lista e peça ao cliente para indicar qual é. "
                    + "Não peça CPF: o TJRS não consulta processo por CPF.";
            case PROCESS_LIST -> result.getMarkdown()
                    + "\n\nEstes são os processos encontrados na consulta por nome do TJRS. "
                    + "Peça ao cliente o número do processo desejado e consulte o andamento por "
                    + "esse número.";
        };
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * The message of the deepest cause, so the first log line states why the
     * lookup failed (timeout, empty page, Playwright error) without opening the stack.
     */
    private static String failureDetail(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            return root.getClass().getSimpleName();
        }
        return message;
    }
}
