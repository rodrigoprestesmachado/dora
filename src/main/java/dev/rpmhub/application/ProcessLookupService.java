/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */
package dev.rpmhub.application;

import java.util.Optional;

import dev.rpmhub.domain.model.ProcessLookupResult;
import dev.rpmhub.domain.port.in.ProcessLookupUseCase;
import dev.rpmhub.domain.port.out.ProcessLookupException;
import dev.rpmhub.domain.port.out.ProcessLookupPort;
import dev.rpmhub.domain.validation.CnjProcessNumberValidator;
import io.quarkus.logging.Log;

/**
 * Application service that validates a process number, delegates the lookup
 * to a {@link ProcessLookupPort} and translates every outcome (success,
 * invalid input, not found, technical failure) into a clear message.
 *
 * <p>This class is deliberately framework-agnostic (plain Java) so it can be
 * unit tested without a CDI container. Its lifecycle and wiring are handled by
 * {@code dev.rpmhub.adapter.config.ApplicationBeans}.
 *
 * @author Rodrigo Prestes Machado
 */
public class ProcessLookupService implements ProcessLookupUseCase {

    /** Port used to perform the actual lookup against the court's search system. */
    private final ProcessLookupPort processLookupPort;

    /**
     * Creates a ProcessLookupService with the given lookup port.
     *
     * @param processLookupPort port used to look up a process by its CNJ number
     */
    public ProcessLookupService(ProcessLookupPort processLookupPort) {
        this.processLookupPort = processLookupPort;
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

        try {
            Optional<ProcessLookupResult> result = processLookupPort.lookup(normalized);
            if (result.isEmpty()) {
                return "Não foi possível localizar o processo " + normalized + " no TJRS. "
                        + "Ele pode não existir, estar em segredo de justiça, ou tramitar em outro tribunal. "
                        + "Confirme o número com o cliente.";
            }
            return result.get().getMarkdown();
        } catch (ProcessLookupException e) {
            Log.warn("⚠️ Falha ao consultar o processo " + normalized + " no TJRS", e);
            return "Não foi possível consultar o processo " + normalized + " agora: o site do TJRS "
                    + "pode estar indisponível ou ter mudado de layout. Tente novamente em alguns minutos.";
        } catch (Exception e) {
            Log.error("❌ Erro inesperado ao consultar o processo " + normalized + " no TJRS", e);
            return "Ocorreu um erro inesperado ao consultar o processo " + normalized + ". "
                    + "Tente novamente mais tarde.";
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
