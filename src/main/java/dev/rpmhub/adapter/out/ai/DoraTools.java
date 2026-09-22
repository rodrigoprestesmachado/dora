/*
 * Copyright (c) 2026 Rodrigo Prestes Machado
 * All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, distribution, or use
 * of this software, via any medium, is strictly prohibited
 * without the express prior written permission of the copyright holder.
 */
package dev.rpmhub.adapter.out.ai;

import dev.langchain4j.agent.tool.Tool;
import dev.rpmhub.domain.port.in.ProcessLookupUseCase;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * LangChain4j tools exposed to {@link DoraAgent}. Each {@code @Tool} method is
 * a thin adapter that delegates to a driving port (use case) so the actual
 * logic stays framework-agnostic and unit-testable.
 *
 * @author Rodrigo Prestes Machado
 */
@ApplicationScoped
public class DoraTools {

    /** Use case that looks up a legal process by its CNJ number on the TJRS website. */
    private final ProcessLookupUseCase processLookupUseCase;

    /**
     * Creates DoraTools with the given use cases.
     *
     * @param processLookupUseCase use case used to look up a legal process by number
     */
    @Inject
    public DoraTools(ProcessLookupUseCase processLookupUseCase) {
        this.processLookupUseCase = processLookupUseCase;
    }

    /**
     * Looks up the current status of a legal process at TJRS (Tribunal de
     * Justiça do Rio Grande do Sul, the Rio Grande do Sul state court) by its
     * CNJ process number.
     *
     * <p>Use this whenever a client asks about the status, progress or latest
     * movements of a legal case and provides (or can provide) the process
     * number. Ask the client for the process number first if they have not
     * given one; it should look like {@code 5033013-66.2026.8.21.0022}.
     *
     * @param processNumber the CNJ process number, with or without punctuation
     * @return Markdown with the process status/movements, or a clear explanation
     *         if the number is invalid, the process was not found, or the lookup failed
     */
    @Tool("Consulta no TJRS (Tribunal de Justiça do RS) o status e o histórico de movimentações "
            + "de um processo judicial, a partir do número do processo no formato CNJ "
            + "(ex.: 5033013-66.2026.8.21.0022).")
    public String lookupTjrsProcess(String processNumber) {
        Log.info("🔧 [TOOL CALL] lookupTjrsProcess invoked by the agent. processNumber=" + processNumber);
        String result = processLookupUseCase.lookup(processNumber);
        Log.info("🔧 [TOOL CALL] lookupTjrsProcess finished. processNumber=" + processNumber);
        return result;
    }
}
